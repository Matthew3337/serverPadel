package com.padelbooking.api.service;

import com.padelbooking.api.dto.PartitaDTO;
import com.padelbooking.api.exception.AccessoNegatoException;
import com.padelbooking.api.exception.BusinessRuleException;
import com.padelbooking.api.exception.ResourceNotFoundException;
import com.padelbooking.api.model.Partecipazione;
import com.padelbooking.api.model.Partita;
import com.padelbooking.api.model.StatoPartita;
import com.padelbooking.api.model.TipoPartecipazione;
import com.padelbooking.api.model.TipoPartita;
import com.padelbooking.api.model.Utente;
import com.padelbooking.api.repository.PartitaRepository;
import com.padelbooking.api.repository.UtenteRepository;
import org.springframework.dao.DataIntegrityViolationException;
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

    // Vincolo unico (id_partita, telefono_utente) su partecipazione: un utente occupa al massimo un posto per partita
    private static final String VINCOLO_PARTECIPAZIONE_UNICA = "uk_partecipazione_utente";

    // Messaggi dell'ingresso (RNF_U_3): fanno parte del contratto con il client
    private static final String MESSAGGIO_AL_COMPLETO = "La partita è al completo";
    private static final String MESSAGGIO_INGRESSO_CHIUSO =
            "L'ingresso è chiuso: mancano meno di " + RegoleTempoPartita.getLimiteModificheOre() + " ore all'inizio";
    private static final String MESSAGGIO_PARTECIPI_GIA = "Partecipi già a questa partita";
    private static final String MESSAGGIO_NON_PIU_APERTA = "La partita non è più aperta";
    private static final String MESSAGGIO_ADMIN_NON_GIOCA = "L'amministratore non può entrare nelle partite";

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

        if (!isAdmin && partecipazione.isEmpty()
                && !visibileDaNonPartecipante(partita, utenteRepository.findById(telefonoRichiedente).orElse(null))) {
            throw partitaNonTrovata(idPartita);
        }

        PartitaDTO.AzioniResponse azioni = isAdmin
                // L'admin gestisce le partite dalla sua pagina: qui non ha azioni
                ? new PartitaDTO.AzioniResponse(false, false, false, false, false)
                : calcolaAzioni(partita, partecipazione.orElse(null), LocalDateTime.now(clock));

        return toDettaglioResponse(partita, azioni);
    }

    // ============================================
    // Bacheca partite pubbliche
    // ============================================
    // RF_GP_11: le pubbliche ancora aperte all'ingresso, con un posto libero e un creatore di livello
    // compatibile, in cui l'utente non gioca già. L'admin non gioca, quindi la sua bacheca è vuota.
    @Transactional(readOnly = true)
    public List<PartitaDTO.BachecaResponse> getBachecaPubbliche(String telefonoRichiedente, boolean isAdmin) {
        if (isAdmin) {
            return List.of();
        }
        Optional<Utente> richiedente = utenteRepository.findById(telefonoRichiedente);
        if (richiedente.isEmpty()) {
            return List.of();
        }
        LocalDateTime adesso = LocalDateTime.now(clock);

        // Una partita ancora aperta inizia dopo adesso + 24 h, cioè da domani in poi; dato che l'inizio
        // effettivo cade al più il giorno dopo data_partita, basta prendere data_partita da oggi in poi.
        // Il filtro preciso e l'ordinamento si fanno sull'inizio effettivo: con data_partita e ora_inizio
        // una partita alle 00:30 della serata finirebbe prima di quella delle 18:00 dello stesso giorno.
        return partitaRepository.findByTipoAndStatoDaData(TipoPartita.PUBBLICA, StatoPartita.CREATA,
                        adesso.toLocalDate()).stream()
                .filter(p -> RegoleTempoPartita.primaDelLimiteModifiche(p, adesso))
                .filter(p -> livelloCompatibile(richiedente.get(), p.getCreatore()))
                .filter(p -> p.getPartecipazioni().size() < POSTI_MAX)
                .filter(p -> trovaPartecipazioneUtente(p, telefonoRichiedente).isEmpty())
                // A parità di inizio (campi diversi) l'id rende l'ordine stabile
                .sorted(Comparator.comparing((Partita p) -> RegoleTempoPartita.inizioEffettivo(p))
                        .thenComparing(Partita::getId))
                .map(this::toBachecaResponse)
                .toList();
    }

    // ============================================
    // Ingresso in una partita pubblica
    // ============================================
    // RF_GP_12, RF_GP_13: l'utente entra subito, senza approvazione del creatore, se la partita è creata,
    // ha un posto libero e mancano più di 24 ore all'inizio. Una partita che non vedrebbe in dettaglio
    // dà lo stesso 404 di GET /api/partite/{id}.
    @Transactional
    public PartitaDTO.DettaglioResponse entra(Integer idPartita, String telefonoRichiedente, boolean isAdmin) {
        if (isAdmin) {
            throw new AccessoNegatoException(MESSAGGIO_ADMIN_NON_GIOCA);
        }

        // Il lock deve essere la prima lettura della transazione: con REPEATABLE READ InnoDB fissa la
        // fotografia dei dati alla prima lettura non bloccante, quindi leggendo prima qualcos'altro
        // le partecipazioni caricate dopo il lock potrebbero non includere quella appena aggiunta
        // da un ingresso concorrente, e si arriverebbe a 5 giocatori
        Partita partita = partitaRepository.findByIdPerAggiornamento(idPartita)
                .orElseThrow(() -> partitaNonTrovata(idPartita));

        // Prima della visibilità: chi partecipa vede sempre la partita, anche privata
        if (trovaPartecipazioneUtente(partita, telefonoRichiedente).isPresent()) {
            throw new BusinessRuleException(MESSAGGIO_PARTECIPI_GIA);
        }

        Utente richiedente = utenteRepository.findById(telefonoRichiedente).orElse(null);
        if (!visibileDaNonPartecipante(partita, richiedente)) {
            throw partitaNonTrovata(idPartita);
        }
        if (partita.getStato() != StatoPartita.CREATA) {
            throw new BusinessRuleException(MESSAGGIO_NON_PIU_APERTA);
        }
        if (partita.getPartecipazioni().size() >= POSTI_MAX) {
            throw new BusinessRuleException(MESSAGGIO_AL_COMPLETO);
        }
        LocalDateTime adesso = LocalDateTime.now(clock);
        if (!RegoleTempoPartita.primaDelLimiteModifiche(partita, adesso)) {
            throw new BusinessRuleException(MESSAGGIO_INGRESSO_CHIUSO);
        }

        // Entra da solo dalla bacheca, quindi non è un invito; la conferma arriva tra -24 h e -12 h
        Partecipazione nuova = new Partecipazione(TipoPartecipazione.UTENTE, richiedente, false, false);
        partita.aggiungiPartecipazione(nuova);
        try {
            // La partita è già gestita dall'EntityManager: il flush inserisce la nuova partecipazione
            // grazie al cascade, senza il merge di save() che la sostituirebbe con una copia
            partitaRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            // Rete di sicurezza oltre al lock: il vincolo unico sul DB impedisce il doppio posto
            if (violaVincolo(ex, VINCOLO_PARTECIPAZIONE_UNICA)) {
                throw new BusinessRuleException(MESSAGGIO_PARTECIPI_GIA);
            }
            throw ex;
        }

        return toDettaglioResponse(partita, calcolaAzioni(partita, nuova, adesso));
    }

    // ============================================
    // Helper privati
    // ============================================

    // Un non partecipante vede solo le pubbliche non annullate di livello compatibile (RF_GP_9).
    // richiedente è null se il telefono del token non corrisponde più a un utente.
    private boolean visibileDaNonPartecipante(Partita partita, Utente richiedente) {
        if (partita.getTipo() != TipoPartita.PUBBLICA || partita.getStato() == StatoPartita.ANNULLATA) {
            return false;
        }
        return richiedente != null && livelloCompatibile(richiedente, partita.getCreatore());
    }

    // I livelli sono multipli di 0,5 (vincolo ck_utente_livello), quindi esatti in double
    private boolean livelloCompatibile(Utente richiedente, Utente creatore) {
        return Math.abs(richiedente.getLivello() - creatore.getLivello()) <= DIFFERENZA_LIVELLO_MAX;
    }

    // Cerca il nome del vincolo nella catena delle cause, come in PrenotazioneService
    private boolean violaVincolo(Throwable ex, String nomeVincolo) {
        for (Throwable causa = ex; causa != null; causa = causa.getCause()) {
            if (causa.getMessage() != null && causa.getMessage().contains(nomeVincolo)) {
                return true;
            }
        }
        return false;
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

    private PartitaDTO.BachecaResponse toBachecaResponse(Partita p) {
        Utente creatore = p.getCreatore();
        return new PartitaDTO.BachecaResponse(
                p.getId(),
                p.getCampo().getId(),
                p.getCampo().getNome(),
                p.getDataPartita(),
                p.getOraInizio(),
                p.getOraFine(),
                creatore.getNome(),
                creatore.getCognome(),
                creatore.getLivello(),
                POSTI_MAX - p.getPartecipazioni().size()
        );
    }
}
