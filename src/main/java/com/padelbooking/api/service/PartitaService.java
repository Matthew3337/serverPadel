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
import com.padelbooking.api.repository.PartecipazioneRepository;
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

    // Messaggi di eliminazione e uscita (RNF_U_3): fanno parte del contratto con il client
    private static final String MESSAGGIO_ADMIN_NON_ELIMINA = "L'amministratore non può eliminare le partite da qui";
    private static final String MESSAGGIO_SOLO_CREATORE_ELIMINA = "Solo il creatore può eliminare la partita";
    // UC_GP_6, flusso di errore 2.1.1: oltre il limite si indica che resta solo l'annullamento dell'admin
    private static final String MESSAGGIO_ELIMINAZIONE_CHIUSA =
            "L'eliminazione è chiusa: mancano meno di " + RegoleTempoPartita.getLimiteModificheOre()
                    + " ore all'inizio. Solo l'amministratore può annullare la partita";
    private static final String MESSAGGIO_ADMIN_NON_ESCE = "L'amministratore non può uscire dalle partite";
    private static final String MESSAGGIO_NON_PARTECIPI = "Non partecipi a questa partita";
    private static final String MESSAGGIO_CREATORE_NON_ESCE = "Il creatore non può uscire dalla partita: può eliminarla";
    private static final String MESSAGGIO_USCITA_CHIUSA =
            "L'uscita è chiusa: mancano meno di " + RegoleTempoPartita.getLimiteModificheOre() + " ore all'inizio";

    // Esito della partita per l'utente nello storico: fanno parte del contratto con il client
    private static final String ESITO_VINTA = "VINTA";
    private static final String ESITO_PERSA = "PERSA";

    private final PartitaRepository partitaRepository;
    private final PartecipazioneRepository partecipazioneRepository;
    private final UtenteRepository utenteRepository;
    private final Clock clock;

    public PartitaService(PartitaRepository partitaRepository,
                          PartecipazioneRepository partecipazioneRepository,
                          UtenteRepository utenteRepository,
                          Clock clock) {
        this.partitaRepository = partitaRepository;
        this.partecipazioneRepository = partecipazioneRepository;
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
    // Storico partite
    // ============================================
    // RF_GP_18, RF_GP_19: le partite non annullate in cui l'utente gioca, future e passate, dalla più
    // recente, con il risultato se registrato. L'admin non gioca, quindi il suo storico è vuoto.
    @Transactional(readOnly = true)
    public List<PartitaDTO.StoricoResponse> getStorico(String telefonoRichiedente, boolean isAdmin) {
        if (isAdmin) {
            return List.of();
        }
        // Una sola query: partita e campo arrivano in JOIN FETCH con la partecipazione dell'utente,
        // che porta già squadra e punti; le altre partecipazioni della partita non servono
        return partecipazioneRepository.findStoricoByTelefono(telefonoRichiedente, TipoPartecipazione.UTENTE,
                        StatoPartita.ANNULLATA).stream()
                // Ordine sull'inizio effettivo, come la bacheca ma decrescente: con data_partita e ora_inizio
                // una partita alle 00:30 della serata risulterebbe precedente a quella delle 18:00.
                // A parità di inizio (campi diversi) l'id rende l'ordine stabile
                .sorted(Comparator.comparing((Partecipazione pa) -> RegoleTempoPartita.inizioEffettivo(pa.getPartita()))
                        .thenComparing(pa -> pa.getPartita().getId())
                        .reversed())
                .map(this::toStoricoResponse)
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
    // Eliminazione della partita
    // ============================================
    // RF_GP_14, RF_GP_15: il creatore elimina la partita fino a 24 ore prima dell'inizio e la partita
    // passa ad ANNULLATA per tutti. La riga resta in tabella: con stato ANNULLATA la colonna generata
    // slot_attivo diventa NULL e lo slot torna prenotabile. L'admin annulla dalla sua gestione (T11).
    @Transactional
    public void elimina(Integer idPartita, String telefonoRichiedente, boolean isAdmin) {
        if (isAdmin) {
            throw new AccessoNegatoException(MESSAGGIO_ADMIN_NON_ELIMINA);
        }

        // Lock come prima lettura, come in entra: un ingresso concorrente aspetta la fine di questa
        // transazione e poi trova la partita ANNULLATA, invece di entrare in una partita appena eliminata
        Partita partita = partitaRepository.findByIdPerAggiornamento(idPartita)
                .orElseThrow(() -> partitaNonTrovata(idPartita));
        Partecipazione partecipazione = verificaVisibileOLancia(partita, telefonoRichiedente);

        LocalDateTime adesso = LocalDateTime.now(clock);
        Optional<RuntimeException> rifiuto = rifiutoEliminazione(partita, partecipazione, adesso);
        if (rifiuto.isPresent()) {
            throw rifiuto.get();
        }

        // La partita è gestita dall'EntityManager: il cambio di stato lo scrive il dirty checking al commit
        partita.setStato(StatoPartita.ANNULLATA);

        // T13: notifiche di annullamento (RF_GN_3, RF_GN_7)
    }

    // ============================================
    // Uscita dalla partita
    // ============================================
    // RF_GP_16, RF_GP_17: un partecipante diverso dal creatore esce fino a 24 ore prima dell'inizio.
    // Il posto si libera e lo stato della partita non cambia: una pubblica torna in bacheca.
    @Transactional
    public void esci(Integer idPartita, String telefonoRichiedente, boolean isAdmin) {
        if (isAdmin) {
            throw new AccessoNegatoException(MESSAGGIO_ADMIN_NON_ESCE);
        }

        // Lock come prima lettura, come in entra: uscita e ingresso sulla stessa partita si serializzano,
        // quindi il conteggio dei posti di un ingresso concorrente vede già il posto liberato
        Partita partita = partitaRepository.findByIdPerAggiornamento(idPartita)
                .orElseThrow(() -> partitaNonTrovata(idPartita));
        Partecipazione partecipazione = verificaVisibileOLancia(partita, telefonoRichiedente);

        LocalDateTime adesso = LocalDateTime.now(clock);
        Optional<RuntimeException> rifiuto = rifiutoUscita(partita, partecipazione, adesso);
        if (rifiuto.isPresent()) {
            throw rifiuto.get();
        }

        // orphanRemoval su Partita.partecipazioni: togliere la partecipazione dalla lista ne cancella
        // la riga al flush del commit, così il vincolo unico uk_partecipazione_utente non blocca
        // un nuovo ingresso dello stesso utente
        partita.rimuoviPartecipazione(partecipazione);
    }

    // ============================================
    // Helper privati
    // ============================================

    // Stessa visibilità di GET /api/partite/{id} per chi non è admin: chi partecipa vede sempre la partita,
    // gli altri solo se la vedrebbero in dettaglio, altrimenti 404. Restituisce la partecipazione
    // del richiedente, null se non partecipa.
    private Partecipazione verificaVisibileOLancia(Partita partita, String telefonoRichiedente) {
        Optional<Partecipazione> partecipazione = trovaPartecipazioneUtente(partita, telefonoRichiedente);
        if (partecipazione.isEmpty()
                && !visibileDaNonPartecipante(partita, utenteRepository.findById(telefonoRichiedente).orElse(null))) {
            throw partitaNonTrovata(partita.getId());
        }
        return partecipazione.orElse(null);
    }

    // Motivo per cui il richiedente (non admin, partita già visibile) non può eliminare la partita,
    // vuoto se può. Lo usano sia elimina sia calcolaAzioni per puoEliminare: le condizioni stanno
    // in un solo punto, così il bottone del dettaglio e l'endpoint non possono divergere.
    private Optional<RuntimeException> rifiutoEliminazione(Partita partita, Partecipazione partecipazione,
                                                           LocalDateTime adesso) {
        if (!eCreatore(partita, partecipazione)) {
            return Optional.of(new AccessoNegatoException(MESSAGGIO_SOLO_CREATORE_ELIMINA));
        }
        if (partita.getStato() != StatoPartita.CREATA) {
            return Optional.of(new BusinessRuleException(MESSAGGIO_NON_PIU_APERTA));
        }
        if (!RegoleTempoPartita.primaDelLimiteModifiche(partita, adesso)) {
            return Optional.of(new BusinessRuleException(MESSAGGIO_ELIMINAZIONE_CHIUSA));
        }
        return Optional.empty();
    }

    // Come rifiutoEliminazione, per l'uscita: condivisa da esci e da puoUscire
    private Optional<RuntimeException> rifiutoUscita(Partita partita, Partecipazione partecipazione,
                                                     LocalDateTime adesso) {
        if (partecipazione == null) {
            return Optional.of(new BusinessRuleException(MESSAGGIO_NON_PARTECIPI));
        }
        if (eCreatore(partita, partecipazione)) {
            return Optional.of(new BusinessRuleException(MESSAGGIO_CREATORE_NON_ESCE));
        }
        if (partita.getStato() != StatoPartita.CREATA) {
            return Optional.of(new BusinessRuleException(MESSAGGIO_NON_PIU_APERTA));
        }
        if (!RegoleTempoPartita.primaDelLimiteModifiche(partita, adesso)) {
            return Optional.of(new BusinessRuleException(MESSAGGIO_USCITA_CHIUSA));
        }
        return Optional.empty();
    }

    // Creatore = il richiedente occupa il posto UTENTE del creatore
    private boolean eCreatore(Partita partita, Partecipazione partecipazione) {
        return partecipazione != null
                && partecipazione.getUtente().getTelefono().equals(partita.getCreatore().getTelefono());
    }

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
        boolean creatore = eCreatore(partita, partecipazione);
        boolean creata = partita.getStato() == StatoPartita.CREATA;
        boolean confermata = partita.getStato() == StatoPartita.CONFERMATA;
        boolean primaDelLimite = RegoleTempoPartita.primaDelLimiteModifiche(partita, adesso);

        boolean puoInvitare = creatore && creata && primaDelLimite
                && partita.getPartecipazioni().size() < POSTI_MAX;
        boolean puoEliminare = rifiutoEliminazione(partita, partecipazione, adesso).isEmpty();
        boolean puoUscire = rifiutoUscita(partita, partecipazione, adesso).isEmpty();
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

    private PartitaDTO.StoricoResponse toStoricoResponse(Partecipazione pa) {
        Partita p = pa.getPartita();
        return new PartitaDTO.StoricoResponse(
                p.getId(),
                p.getCampo().getId(),
                p.getCampo().getNome(),
                p.getDataPartita(),
                p.getOraInizio(),
                p.getOraFine(),
                p.getStato().name().toLowerCase(Locale.ROOT),
                p.getTipo().name(),
                toRisultatoResponse(pa)
        );
    }

    // null finché il risultato non è registrato. Il vincitore lo stabilisce RegoleRisultatoPartita;
    // l'esito resta null se l'utente non ha una squadra o se i set non indicano un vincitore.
    private PartitaDTO.RisultatoResponse toRisultatoResponse(Partecipazione pa) {
        Partita p = pa.getPartita();
        if (!RegoleRisultatoPartita.risultatoRegistrato(p)) {
            return null;
        }
        List<RegoleRisultatoPartita.SetGiocato> setGiocati = RegoleRisultatoPartita.setGiocati(p);
        List<PartitaDTO.SetResponse> set = setGiocati.stream()
                .map(s -> new PartitaDTO.SetResponse(s.squadra1(), s.squadra2()))
                .toList();

        Integer squadraUtente = pa.getSquadra();
        Integer vincitrice = RegoleRisultatoPartita.squadraVincitrice(setGiocati);
        String esito = null;
        if (squadraUtente != null && vincitrice != null) {
            esito = squadraUtente.equals(vincitrice) ? ESITO_VINTA : ESITO_PERSA;
        }
        return new PartitaDTO.RisultatoResponse(set, squadraUtente, esito, pa.getPuntiAssegnati());
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
