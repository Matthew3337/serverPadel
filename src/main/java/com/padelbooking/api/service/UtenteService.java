package com.padelbooking.api.service;

import com.padelbooking.api.dto.PartitaDTO;
import com.padelbooking.api.exception.ResourceNotFoundException;
import com.padelbooking.api.model.Partecipazione;
import com.padelbooking.api.model.Partita;
import com.padelbooking.api.model.StatoPartita;
import com.padelbooking.api.model.TipoPartecipazione;
import com.padelbooking.api.model.Utente;
import com.padelbooking.api.repository.NotificaPushRepository;
import com.padelbooking.api.repository.PartitaRepository;
import com.padelbooking.api.repository.UtenteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// Ricerche sugli utenti che non riguardano una partita già esistente (R11) ed eliminazione del proprio account (T18)
@Service
public class UtenteService {

    private static final String MESSAGGIO_UTENTE_NON_TROVATO = "Utente non trovato";

    private final UtenteRepository utenteRepository;
    private final PartitaRepository partitaRepository;
    private final NotificaPushRepository notificaPushRepository;
    private final Clock clock;

    public UtenteService(UtenteRepository utenteRepository,
                         PartitaRepository partitaRepository,
                         NotificaPushRepository notificaPushRepository,
                         Clock clock) {
        this.utenteRepository = utenteRepository;
        this.partitaRepository = partitaRepository;
        this.notificaPushRepository = notificaPushRepository;
        this.clock = clock;
    }

    // ============================================
    // Utenti invitabili alla prenotazione
    // ============================================
    // R11: gli inviti si scelgono prima che la partita esista, quindi l'elenco è quello di
    // GET /api/partite/{id}/invitabili senza il filtro sui posti della partita: gli utenti non admin,
    // escluso chi chiama, che sarà il creatore. Stessa ricerca e stesso ordine (cognome, nome, telefono).
    @Transactional(readOnly = true)
    public List<PartitaDTO.InvitabileResponse> getInvitabili(String cerca, String telefonoRichiedente) {
        return utenteRepository.findNonAdmin(PartitaService.patternRicerca(cerca)).stream()
                .filter(u -> !u.getTelefono().equals(telefonoRichiedente))
                .map(u -> new PartitaDTO.InvitabileResponse(u.getTelefono(), u.getNome(), u.getCognome(),
                        u.getLivello()))
                .toList();
    }

    // ============================================
    // Eliminazione del proprio account
    // ============================================
    // T18: vale per utenti e admin, anche per l'unico admin. Tutto in una transazione: un errore lascia l'account
    // e le partite come erano. Ogni partita collegata si legge con il lock, una alla volta in ordine di id, e
    // solo alla fine si cancella la riga utente: è l'ordine degli altri percorsi (prima la partita, poi le
    // righe utente, come in PunteggiService), quindi non si crea un ciclo di attese.
    // READ COMMITTED invece del REPEATABLE READ di default: qui le partite da bloccare sono più di una, quindi
    // il lock non può essere la prima lettura come in PartitaService.entra; così ogni lettura dopo un lock vede
    // i posti aggiunti o tolti da chi lo teneva prima, invece della fotografia presa all'inizio.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void eliminaAccount(String telefono) {
        Utente utente = utenteRepository.findById(telefono)
                .orElseThrow(() -> new ResourceNotFoundException(MESSAGGIO_UTENTE_NON_TROVATO));

        // Le partite non ancora finite passano al primo admin per telefono; senza altri admin il creatore resta null
        Utente nuovoCreatore = utenteRepository.findAdminTranne(telefono).stream().findFirst().orElse(null);
        LocalDateTime adesso = LocalDateTime.now(clock);

        for (Integer idPartita : partitaRepository.findIdCollegateAUtente(telefono)) {
            // Una partita non può sparire (le annullate restano), ma se mancasse non c'è niente da scollegare
            partitaRepository.findByIdPerAggiornamento(idPartita)
                    .ifPresent(partita -> scollegaDaPartita(partita, telefono, nuovoCreatore, adesso));
        }

        notificaPushRepository.deleteByDestinatario(utente);
        // Le modifiche alle partite vanno scritte prima della DELETE dell'utente, che le chiavi esterne
        // referenziano: Hibernate cancellerebbe l'utente prima dei posti tolti da orphanRemoval
        partitaRepository.flush();
        utenteRepository.delete(utente);
    }

    // ============================================
    // Helper privati
    // ============================================

    // Una partita in corso o futura, non annullata e non giocata, si gioca ancora: l'utente esce e la partita
    // passa a un altro creatore. Altrimenti è storia: il posto resta anonimo, con squadra e punti, così
    // risultati, punti e storico degli altri non cambiano. Una GIOCATA conta come storia anche se l'admin l'ha
    // segnata prima della fine: i suoi punti sono già stati assegnati.
    private void scollegaDaPartita(Partita partita, String telefono, Utente nuovoCreatore, LocalDateTime adesso) {
        boolean ancoraDaGiocare = partita.getStato() != StatoPartita.ANNULLATA
                && partita.getStato() != StatoPartita.GIOCATA
                && !RegoleTempoPartita.finita(partita, adesso);
        Optional<Partecipazione> posto = partita.getPartecipazioni().stream()
                .filter(pa -> pa.getTipo() == TipoPartecipazione.UTENTE && pa.getUtente() != null)
                .filter(pa -> pa.getUtente().getTelefono().equals(telefono))
                .findFirst();
        boolean eraCreatore = partita.getCreatore() != null && partita.getCreatore().getTelefono().equals(telefono);

        if (ancoraDaGiocare) {
            // Come in PartitaService.esci: orphanRemoval cancella la riga, e senza utenti la partita è annullata
            posto.ifPresent(pa -> {
                partita.rimuoviPartecipazione(pa);
                if (!RegoleConfermaPartita.haGiocatoriUtente(partita)) {
                    partita.setStato(StatoPartita.ANNULLATA);
                    // T13: notifiche di annullamento (RF_GN_3, RF_GN_7)
                }
            });
            if (eraCreatore) {
                partita.setCreatore(nuovoCreatore);
            }
        } else {
            posto.ifPresent(pa -> pa.setUtente(null));
            if (eraCreatore) {
                partita.setCreatore(null);
            }
        }

        if (partita.getRegistrante() != null && partita.getRegistrante().getTelefono().equals(telefono)) {
            partita.setRegistrante(null);
        }
    }
}
