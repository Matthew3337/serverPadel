package com.padelbooking.api.service;

import com.padelbooking.api.dto.PartitaDTO;
import com.padelbooking.api.exception.ResourceNotFoundException;
import com.padelbooking.api.model.Partecipazione;
import com.padelbooking.api.model.Partita;
import com.padelbooking.api.model.StatoPartita;
import com.padelbooking.api.model.TipoPartecipazione;
import com.padelbooking.api.model.TipoPartita;
import com.padelbooking.api.model.Utente;
import com.padelbooking.api.repository.PartitaRepository;
import com.padelbooking.api.repository.UtenteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class PartitaService {

    // RF_GP_9: una partita pubblica è visibile a chi ha un livello che differisce al massimo di 1 da quello del creatore
    private static final double DIFFERENZA_LIVELLO_MAX = 1.0;

    // Una partita ha al massimo 4 posti (UTENTE o CAMPO)
    private static final int POSTI_MAX = 4;

    private final PartitaRepository partitaRepository;
    private final UtenteRepository utenteRepository;
    private final Clock clock;

    public PartitaService(PartitaRepository partitaRepository,
                          UtenteRepository utenteRepository,
                          Clock clock) {
        this.partitaRepository = partitaRepository;
        this.utenteRepository = utenteRepository;
        this.clock = clock;
    }

    // ============================================
    // Dettaglio partita
    // ============================================
    // Una partita non visibile dà lo stesso 404 di una inesistente, così non si rivela che esiste
    @Transactional(readOnly = true)
    public PartitaDTO.DettaglioResponse getDettaglio(Integer idPartita, String telefonoRichiedente, boolean isAdmin) {
        Partita partita = partitaRepository.findById(idPartita)
                .orElseThrow(() -> partitaNonTrovata(idPartita));

        Optional<Partecipazione> partecipazione = trovaPartecipazioneUtente(partita, telefonoRichiedente);

        if (!isAdmin && partecipazione.isEmpty() && !visibileDaNonPartecipante(partita, telefonoRichiedente)) {
            throw partitaNonTrovata(idPartita);
        }

        PartitaDTO.AzioniResponse azioni = isAdmin
                // L'admin gestisce le partite dalla sua pagina: qui non ha azioni
                ? new PartitaDTO.AzioniResponse(false, false, false, false, false)
                : calcolaAzioni(partita, partecipazione.orElse(null), LocalDateTime.now(clock));

        return toDettaglioResponse(partita, azioni);
    }

    // ============================================
    // Helper privati
    // ============================================

    // Un non partecipante vede solo le pubbliche non annullate di livello compatibile (RF_GP_9)
    private boolean visibileDaNonPartecipante(Partita partita, String telefonoRichiedente) {
        if (partita.getTipo() != TipoPartita.PUBBLICA || partita.getStato() == StatoPartita.ANNULLATA) {
            return false;
        }
        Optional<Utente> richiedente = utenteRepository.findById(telefonoRichiedente);
        if (richiedente.isEmpty()) {
            return false;
        }
        // I livelli sono multipli di 0,5 (vincolo ck_utente_livello), quindi esatti in double
        double differenza = Math.abs(richiedente.get().getLivello() - partita.getCreatore().getLivello());
        return differenza <= DIFFERENZA_LIVELLO_MAX;
    }

    // Le azioni dipendono dal ruolo nella partita (creatore, altro partecipante, esterno),
    // dallo stato e dall'istante attuale rispetto all'inizio e alla fine effettivi
    private PartitaDTO.AzioniResponse calcolaAzioni(Partita partita, Partecipazione partecipazione, LocalDateTime adesso) {
        boolean partecipante = partecipazione != null;
        boolean creatore = partecipante
                && partecipazione.getUtente().getTelefono().equals(partita.getCreatore().getTelefono());
        boolean creata = partita.getStato() == StatoPartita.CREATA;
        boolean confermata = partita.getStato() == StatoPartita.CONFERMATA;
        boolean primaDelLimite = RegoleTempoPartita.primaDelLimiteModifiche(partita, adesso);

        boolean puoInvitare = creatore && creata && primaDelLimite
                && partita.getPartecipazioni().size() < POSTI_MAX;
        boolean puoEliminare = creatore && creata && primaDelLimite;
        boolean puoUscire = partecipante && !creatore && creata && primaDelLimite;
        boolean puoConfermare = partecipante && !Boolean.TRUE.equals(partecipazione.getConfermata())
                && creata && RegoleTempoPartita.inFinestraConferma(partita, adesso);
        boolean puoRegistrareRisultato = partecipante && confermata
                && RegoleTempoPartita.inFinestraRisultato(partita, adesso);

        return new PartitaDTO.AzioniResponse(puoInvitare, puoEliminare, puoUscire, puoConfermare, puoRegistrareRisultato);
    }

    // Partecipazione UTENTE del richiedente, se occupa un posto nella partita
    private Optional<Partecipazione> trovaPartecipazioneUtente(Partita partita, String telefono) {
        return partita.getPartecipazioni().stream()
                .filter(pa -> pa.getTipo() == TipoPartecipazione.UTENTE)
                .filter(pa -> pa.getUtente().getTelefono().equals(telefono))
                .findFirst();
    }

    private ResourceNotFoundException partitaNonTrovata(Integer idPartita) {
        return new ResourceNotFoundException("Partita non trovata con id " + idPartita);
    }

    // Il creatore per primo, poi gli altri posti in ordine di id (@OrderBy su Partita.partecipazioni):
    // l'ordinamento è stabile, quindi sposta solo il creatore
    private PartitaDTO.DettaglioResponse toDettaglioResponse(Partita p, PartitaDTO.AzioniResponse azioni) {
        String telefonoCreatore = p.getCreatore().getTelefono();
        List<PartitaDTO.PostoResponse> posti = p.getPartecipazioni().stream()
                .sorted(Comparator.comparing((Partecipazione pa) -> !eDelCreatore(pa, telefonoCreatore)))
                .map(this::toPostoResponse)
                .toList();

        return new PartitaDTO.DettaglioResponse(
                p.getId(),
                p.getCampo().getId(),
                p.getCampo().getNome(),
                p.getDataPartita(),
                p.getOraInizio(),
                p.getOraFine(),
                p.getStato().name().toLowerCase(Locale.ROOT),
                p.getTipo().name(),
                telefonoCreatore,
                posti,
                azioni
        );
    }

    private boolean eDelCreatore(Partecipazione pa, String telefonoCreatore) {
        return pa.getTipo() == TipoPartecipazione.UTENTE && pa.getUtente().getTelefono().equals(telefonoCreatore);
    }

    private PartitaDTO.PostoResponse toPostoResponse(Partecipazione pa) {
        Utente u = pa.getUtente();
        if (pa.getTipo() == TipoPartecipazione.CAMPO || u == null) {
            return new PartitaDTO.PostoResponse(pa.getTipo().name(), null, null, null, null, pa.getConfermata());
        }
        return new PartitaDTO.PostoResponse(pa.getTipo().name(), u.getTelefono(), u.getNome(), u.getCognome(),
                u.getLivello(), pa.getConfermata());
    }
}
