package com.padelbooking.api.service;

import com.padelbooking.api.dto.PartitaDTO;
import com.padelbooking.api.exception.AccessoNegatoException;
import com.padelbooking.api.exception.BusinessRuleException;
import com.padelbooking.api.exception.ResourceNotFoundException;
import com.padelbooking.api.exception.RichiestaNonValidaException;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PartitaService {

    // RF_GP_9: una partita pubblica è visibile a chi ha un livello che differisce al massimo di 1 da quello
    // del creatore; se il creatore è un admin, che non ha un livello di gioco, è visibile a tutti i livelli
    private static final double DIFFERENZA_LIVELLO_MAX = 1.0;

    // Una partita ha al massimo 4 posti (UTENTE o CAMPO): la soglia sta in RegoleConfermaPartita
    private static final int POSTI_MAX = RegoleConfermaPartita.getPostiMax();

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

    // Messaggi degli inviti (RNF_U_3): fanno parte del contratto con il client
    private static final String MESSAGGIO_SOLO_CREATORE_INVITA = "Solo il creatore può invitare";
    // D3: il creatore invita fino all'inizio effettivo, non più fino a −24 h
    private static final String MESSAGGIO_INVITI_CHIUSI = "Gli inviti sono chiusi: la partita è già iniziata";
    private static final String MESSAGGIO_INVITATO_PARTECIPA_GIA = "L'utente partecipa già a questa partita";
    private static final String MESSAGGIO_NON_INVITA_ADMIN = "Non è possibile invitare un amministratore";
    private static final String MESSAGGIO_UTENTE_NON_TROVATO = "Utente non trovato";

    // Messaggi del cambio di visibilità (RNF_U_3, D8): fanno parte del contratto con il client
    private static final String MESSAGGIO_SOLO_CREATORE_TIPO = "Solo il creatore può modificare la visibilità";
    private static final String MESSAGGIO_GIA_INIZIATA = "La partita è già iniziata";
    private static final String MESSAGGIO_TIPO_OBBLIGATORIO = "Il tipo è obbligatorio";
    private static final String MESSAGGIO_TIPO_NON_VALIDO = "Il tipo deve essere PUBBLICA o PRIVATA";

    // Messaggi della conferma (RNF_U_3): fanno parte del contratto con il client
    private static final String MESSAGGIO_ADMIN_NON_CONFERMA = "L'amministratore non conferma le partite";
    private static final String MESSAGGIO_GIA_CONFERMATO = "Hai già confermato la partecipazione";
    private static final String MESSAGGIO_CONFERMA_FUORI_FINESTRA =
            "La conferma è possibile solo tra 24 e 12 ore prima dell'inizio";

    // Messaggi della registrazione del risultato (RNF_U_3): fanno parte del contratto con il client.
    // Quelli sui set stanno in RegoleRisultatoPartita
    private static final String MESSAGGIO_ADMIN_NON_REGISTRA = "L'amministratore modifica i risultati dalla propria area";
    private static final String MESSAGGIO_RISULTATO_GIA_REGISTRATO = "Il risultato è già stato registrato";
    private static final String MESSAGGIO_RISULTATO_SOLO_CONFERMATE =
            "Il risultato si può registrare solo per le partite confermate";
    private static final String MESSAGGIO_PARTITA_NON_FINITA = "La partita non è ancora finita";
    private static final String MESSAGGIO_TERMINE_RISULTATO_SCADUTO = "Il termine per registrare il risultato è scaduto";
    private static final String MESSAGGIO_GIOCATORI_PER_SQUADRA = "Ogni squadra deve avere 2 giocatori";
    private static final String MESSAGGIO_SQUADRE_POSTI = "Le squadre devono contenere i 4 posti della partita";

    // RF_GR_3: 4 posti divisi in due squadre
    private static final int GIOCATORI_PER_SQUADRA = 2;

    // Carattere di escape per i LIKE della ricerca degli invitabili (vedi UtenteRepository.findInvitabili)
    private static final char ESCAPE_LIKE = '!';

    // RF_GI_6: la bacheca inviti mostra le partite ancora da giocare, non annullate né giocate.
    // Sono anche gli stati in cui il creatore invita (D3) e cambia la visibilità (D8)
    private static final List<StatoPartita> STATI_BACHECA_INVITI = List.of(StatoPartita.CREATA, StatoPartita.CONFERMATA);
    private static final List<StatoPartita> STATI_APERTI_AL_CREATORE = STATI_BACHECA_INVITI;

    // Esito della partita per l'utente nello storico: fanno parte del contratto con il client
    private static final String ESITO_VINTA = "VINTA";
    private static final String ESITO_PERSA = "PERSA";

    private final PartitaRepository partitaRepository;
    private final PartecipazioneRepository partecipazioneRepository;
    private final UtenteRepository utenteRepository;
    private final PunteggiService punteggiService;
    private final Clock clock;

    public PartitaService(PartitaRepository partitaRepository,
                          PartecipazioneRepository partecipazioneRepository,
                          UtenteRepository utenteRepository,
                          PunteggiService punteggiService,
                          Clock clock) {
        this.partitaRepository = partitaRepository;
        this.partecipazioneRepository = partecipazioneRepository;
        this.utenteRepository = utenteRepository;
        this.punteggiService = punteggiService;
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

        if (isAdmin) {
            return dettaglioPerAdmin(partita);
        }
        Partecipazione delRichiedente = partecipazione.orElse(null);
        return toDettaglioResponse(partita, calcolaAzioni(partita, delRichiedente, LocalDateTime.now(clock)), delRichiedente);
    }

    // L'admin gestisce le partite dalla sua pagina: qui non ha azioni, e il risultato non ha il suo punto di
    // vista. Lo usa anche AdminService, con la partita già letta nella sua transazione.
    public PartitaDTO.DettaglioResponse dettaglioPerAdmin(Partita partita) {
        return toDettaglioResponse(partita, new PartitaDTO.AzioniResponse(false, false, false, false, false, false), null);
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
    // RF_GP_18, RF_GP_19, R12, D7: le partite non annullate in cui l'utente gioca e già finite
    // (adesso >= fine effettiva), dalla più recente, comprese quelle senza risultato, che si registra da qui.
    // Quelle non ancora finite stanno in "Le mie partite". L'admin non gioca, quindi il suo storico è vuoto.
    @Transactional(readOnly = true)
    public List<PartitaDTO.StoricoResponse> getStorico(String telefonoRichiedente, boolean isAdmin) {
        if (isAdmin) {
            return List.of();
        }
        LocalDateTime adesso = LocalDateTime.now(clock);
        return partecipazioniNonAnnullate(telefonoRichiedente).stream()
                .filter(pa -> RegoleTempoPartita.finita(pa.getPartita(), adesso))
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
    // Le mie partite
    // ============================================
    // R12: le partite non annullate in cui l'utente gioca e non ancora finite (adesso < fine effettiva),
    // quindi anche quella in corso, dalla più vicina. Stesso formato dello storico: le due liste si
    // dividono le stesse partite sul confine della fine effettiva. L'admin non gioca: lista vuota.
    @Transactional(readOnly = true)
    public List<PartitaDTO.StoricoResponse> getMiePartite(String telefonoRichiedente, boolean isAdmin) {
        if (isAdmin) {
            return List.of();
        }
        LocalDateTime adesso = LocalDateTime.now(clock);
        return partecipazioniNonAnnullate(telefonoRichiedente).stream()
                .filter(pa -> !RegoleTempoPartita.finita(pa.getPartita(), adesso))
                // Ordine sull'inizio effettivo crescente, come la bacheca; a parità di inizio decide l'id
                .sorted(Comparator.comparing((Partecipazione pa) -> RegoleTempoPartita.inizioEffettivo(pa.getPartita()))
                        .thenComparing(pa -> pa.getPartita().getId()))
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

        return toDettaglioResponse(partita, calcolaAzioni(partita, nuova, adesso), nuova);
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
    // Fa eccezione l'uscita dell'ultimo giocatore utente, possibile solo con un creatore admin che non ha
    // un posto: i posti CAMPO da soli non fanno una partita, quindi passa subito ad ANNULLATA (T12).
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

        if (!RegoleConfermaPartita.haGiocatoriUtente(partita)) {
            partita.setStato(StatoPartita.ANNULLATA);
            // T13: notifiche di annullamento (RF_GN_3, RF_GN_7)
        }
    }

    // ============================================
    // Utenti invitabili
    // ============================================
    // UC_GI_1 passo 2: il creatore cerca chi invitare tra gli utenti non admin che non hanno già un posto,
    // di qualunque livello (RF_GI_2). L'admin non invita, anche se è il creatore perché ha tolto dalla
    // partita il creatore originale, quindi riceve subito 403 come in entra, senza leggere la partita.
    @Transactional(readOnly = true)
    public List<PartitaDTO.InvitabileResponse> getInvitabili(Integer idPartita, String cerca,
                                                             String telefonoRichiedente, boolean isAdmin) {
        if (isAdmin) {
            throw new AccessoNegatoException(MESSAGGIO_SOLO_CREATORE_INVITA);
        }
        Partita partita = partitaRepository.findById(idPartita)
                .orElseThrow(() -> partitaNonTrovata(idPartita));
        Partecipazione partecipazione = verificaVisibileOLancia(partita, telefonoRichiedente);
        if (!eCreatore(partita, partecipazione)) {
            throw new AccessoNegatoException(MESSAGGIO_SOLO_CREATORE_INVITA);
        }

        return utenteRepository.findInvitabili(idPartita, patternRicerca(cerca)).stream()
                .map(u -> new PartitaDTO.InvitabileResponse(u.getTelefono(), u.getNome(), u.getCognome(),
                        u.getLivello()))
                .toList();
    }

    // ============================================
    // Invito di un utente
    // ============================================
    // UC_GI_1, RF_GI_1–5, RF_GP_10: il creatore aggiunge subito l'invitato alla partita, pubblica
    // o privata, senza che l'invitato debba accettare. Valgono le stesse regole di puoInvitare.
    @Transactional
    public PartitaDTO.DettaglioResponse invita(Integer idPartita, String telefonoInvitato,
                                               String telefonoRichiedente, boolean isAdmin) {
        if (isAdmin) {
            throw new AccessoNegatoException(MESSAGGIO_SOLO_CREATORE_INVITA);
        }

        // Lock come prima lettura, come in entra: inviti e ingressi sulla stessa partita si serializzano,
        // quindi il conteggio dei posti vede sempre le partecipazioni aggiunte dalle richieste concorrenti
        Partita partita = partitaRepository.findByIdPerAggiornamento(idPartita)
                .orElseThrow(() -> partitaNonTrovata(idPartita));
        Partecipazione partecipazione = verificaVisibileOLancia(partita, telefonoRichiedente);

        LocalDateTime adesso = LocalDateTime.now(clock);
        Optional<RuntimeException> rifiuto = rifiutoInvito(partita, partecipazione, adesso);
        if (rifiuto.isPresent()) {
            throw rifiuto.get();
        }

        Utente invitato = utenteRepository.findById(telefonoInvitato)
                .orElseThrow(() -> new ResourceNotFoundException(MESSAGGIO_UTENTE_NON_TROVATO));
        if (Boolean.TRUE.equals(invitato.getIsAdmin())) {
            throw new BusinessRuleException(MESSAGGIO_NON_INVITA_ADMIN);
        }
        // Copre anche l'invito a se stesso: il creatore ha già un posto
        if (trovaPartecipazioneUtente(partita, telefonoInvitato).isPresent()) {
            throw new BusinessRuleException(MESSAGGIO_INVITATO_PARTECIPA_GIA);
        }

        // La conferma arriva tra -24 h e -12 h, come per chi entra da solo. D2, D3: un invito a finestra
        // di conferma chiusa, o in una partita già CONFERMATA con un posto libero, vale già come conferma
        Partecipazione nuova = new Partecipazione(TipoPartecipazione.UTENTE, invitato, true,
                RegoleConfermaPartita.confermataAllIngresso(partita, adesso));
        partita.aggiungiPartecipazione(nuova);
        try {
            // Come in entra: la partita è gestita dall'EntityManager e il cascade inserisce la partecipazione
            partitaRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            // Rete di sicurezza oltre al lock: il vincolo unico sul DB impedisce il doppio posto
            if (violaVincolo(ex, VINCOLO_PARTECIPAZIONE_UNICA)) {
                throw new BusinessRuleException(MESSAGGIO_INVITATO_PARTECIPA_GIA);
            }
            throw ex;
        }

        // Con il quarto posto e tutti gli utenti confermati la partita passa a CONFERMATA. Se resta un utente
        // entrato prima di −12 h senza confermare, resta CREATA e all'inizio la annulla lo scheduler (D2)
        if (RegoleConfermaPartita.confermaSeCompleta(partita)) {
            // T13: notifica ADMIN_CONFERMA all'amministratore (RF_GN_8)
        }

        // T13: notifica di invito (RF_GN_1)

        return toDettaglioResponse(partita, calcolaAzioni(partita, partecipazione, adesso), partecipazione);
    }

    // ============================================
    // Cambio di visibilità
    // ============================================
    // R13, D8: il creatore passa la partita da pubblica a privata o viceversa fino all'inizio effettivo.
    // Chi è già in partita ci resta: cambia solo chi la vede in bacheca. Valgono le stesse regole di
    // puoCambiareTipo. L'admin non è mai il creatore che gioca, quindi riceve subito 403 come in invita.
    @Transactional
    public PartitaDTO.DettaglioResponse cambiaTipo(Integer idPartita, String tipo, String telefonoRichiedente,
                                                   boolean isAdmin) {
        if (isAdmin) {
            throw new AccessoNegatoException(MESSAGGIO_SOLO_CREATORE_TIPO);
        }
        // Il tipo si controlla prima di leggere la partita: un valore errato non rivela se la partita esiste
        TipoPartita nuovo = tipoDaRichiesta(tipo);

        // Lock come prima lettura, come in entra: un ingresso dalla bacheca concorrente si serializza
        // e trova già il tipo aggiornato
        Partita partita = partitaRepository.findByIdPerAggiornamento(idPartita)
                .orElseThrow(() -> partitaNonTrovata(idPartita));
        Partecipazione partecipazione = verificaVisibileOLancia(partita, telefonoRichiedente);

        LocalDateTime adesso = LocalDateTime.now(clock);
        Optional<RuntimeException> rifiuto = rifiutoCambioTipo(partita, partecipazione, adesso);
        if (rifiuto.isPresent()) {
            throw rifiuto.get();
        }

        // La partita è gestita dall'EntityManager: il cambio lo scrive il dirty checking al commit.
        // Lo stesso tipo di prima non è un errore: la richiesta è già soddisfatta
        partita.setTipo(nuovo);

        return toDettaglioResponse(partita, calcolaAzioni(partita, partecipazione, adesso), partecipazione);
    }

    // ============================================
    // Conferma della partecipazione
    // ============================================
    // UC_GC_1, RF_GC_3–6: un partecipante UTENTE conferma la propria presenza tra 24 e 12 ore prima
    // dell'inizio. Se dopo la sua conferma la partita ha 4 posti e tutti gli utenti hanno confermato,
    // passa a CONFERMATA. Valgono le stesse regole di puoConfermare.
    @Transactional
    public PartitaDTO.DettaglioResponse conferma(Integer idPartita, String telefonoRichiedente, boolean isAdmin) {
        if (isAdmin) {
            throw new AccessoNegatoException(MESSAGGIO_ADMIN_NON_CONFERMA);
        }

        // Lock come prima lettura, come in entra: conferma, uscita e attività pianificata sulla stessa
        // partita si serializzano, quindi il passaggio a CONFERMATA vede i posti e le conferme aggiornati
        Partita partita = partitaRepository.findByIdPerAggiornamento(idPartita)
                .orElseThrow(() -> partitaNonTrovata(idPartita));
        Partecipazione partecipazione = verificaVisibileOLancia(partita, telefonoRichiedente);

        LocalDateTime adesso = LocalDateTime.now(clock);
        Optional<RuntimeException> rifiuto = rifiutoConferma(partita, partecipazione, adesso);
        if (rifiuto.isPresent()) {
            throw rifiuto.get();
        }

        // Partita e partecipazione sono gestite dall'EntityManager: le modifiche le scrive il dirty checking al commit
        partecipazione.setConfermata(true);
        if (RegoleConfermaPartita.confermaSeCompleta(partita)) {
            // T13: notifica ADMIN_CONFERMA all'amministratore (RF_GN_8)
        }

        return toDettaglioResponse(partita, calcolaAzioni(partita, partecipazione, adesso), partecipazione);
    }

    // ============================================
    // Registrazione del risultato
    // ============================================
    // UC_GR_1, RF_GR_1–8: un partecipante UTENTE registra squadre e set tra la fine della partita e 24 ore
    // dopo. Il risultato vale subito, senza conferme degli altri giocatori (RF_GR_7), e la partita passa
    // a GIOCATA. Valgono le stesse regole di puoRegistrareRisultato.
    @Transactional
    public PartitaDTO.DettaglioResponse registraRisultato(Integer idPartita, PartitaDTO.RisultatoRequest request,
                                                          String telefonoRichiedente, boolean isAdmin) {
        if (isAdmin) {
            throw new AccessoNegatoException(MESSAGGIO_ADMIN_NON_REGISTRA);
        }

        // Lock come prima lettura, come in entra: due giocatori che registrano insieme si serializzano,
        // quindi il secondo trova la partita già GIOCATA e riceve il 409 senza sovrascrivere il primo
        Partita partita = partitaRepository.findByIdPerAggiornamento(idPartita)
                .orElseThrow(() -> partitaNonTrovata(idPartita));
        Partecipazione partecipazione = verificaVisibileOLancia(partita, telefonoRichiedente);

        LocalDateTime adesso = LocalDateTime.now(clock);
        Optional<RuntimeException> rifiuto = rifiutoRisultato(partita, partecipazione, adesso);
        if (rifiuto.isPresent()) {
            throw rifiuto.get();
        }

        validaRisultato(partita, request);
        scriviRisultato(partita, request, partecipazione.getUtente(), adesso);

        // UC_GCL_2: nella stessa transazione del risultato, dopo aver scritto set e squadre (RNF_A_7)
        punteggiService.assegnaPunti(partita);

        return toDettaglioResponse(partita, calcolaAzioni(partita, partecipazione, adesso), partecipazione);
    }

    // Le validazioni del risultato, uguali per chi registra e per l'admin che corregge (RF_GR_3, RF_GR_5, RF_GR_6)
    public void validaRisultato(Partita partita, PartitaDTO.RisultatoRequest request) {
        verificaSquadre(partita, request.getSquadra1(), request.getSquadra2());
        Optional<String> setNonValidi = RegoleRisultatoPartita.rifiutoSet(setInseriti(request.getSet()));
        if (setNonValidi.isPresent()) {
            throw new RichiestaNonValidaException(setNonValidi.get());
        }
    }

    // Scrive un risultato già validato e porta la partita a GIOCATA. I punti restano a chi chiama, che
    // deve avere la partita in lock nella propria transazione. Partita e partecipazioni sono gestite
    // dall'EntityManager: le modifiche le scrive il dirty checking al commit.
    // adesso viene dal Clock iniettato, e non dall'orologio del database.
    public void scriviRisultato(Partita partita, PartitaDTO.RisultatoRequest request, Utente registrante,
                                LocalDateTime adesso) {
        scriviSet(partita, setInseriti(request.getSet()));
        partita.setRegistrante(registrante);
        partita.setDataRisultato(adesso);
        for (Partecipazione posto : partita.getPartecipazioni()) {
            posto.setSquadra(request.getSquadra1().contains(posto.getId())
                    ? RegoleRisultatoPartita.SQUADRA_1
                    : RegoleRisultatoPartita.SQUADRA_2);
        }
        partita.setStato(StatoPartita.GIOCATA);
    }

    // ============================================
    // Bacheca inviti
    // ============================================
    // UC_GI_2, RF_GI_6: le partite creata o confermata in cui l'utente è stato invitato, dalla più vicina.
    // L'admin non gioca, quindi la sua bacheca è vuota.
    @Transactional(readOnly = true)
    public List<PartitaDTO.InvitoRicevutoResponse> getBachecaInviti(String telefonoRichiedente, boolean isAdmin) {
        if (isAdmin) {
            return List.of();
        }
        return partecipazioneRepository.findInvitiByTelefono(telefonoRichiedente, TipoPartecipazione.UTENTE,
                        STATI_BACHECA_INVITI).stream()
                .map(Partecipazione::getPartita)
                // Ordine sull'inizio effettivo come nella bacheca pubbliche; a parità di inizio decide l'id
                .sorted(Comparator.comparing((Partita p) -> RegoleTempoPartita.inizioEffettivo(p))
                        .thenComparing(Partita::getId))
                .map(this::toInvitoRicevutoResponse)
                .toList();
    }

    // ============================================
    // Helper privati
    // ============================================
    // patternRicerca, violaVincolo e partitaNonTrovata sono statici e visibili nel package perché li usa
    // anche AdminService. Statici perché un metodo non pubblico chiamato sul proxy di Spring non è garantito
    // che arrivi all'istanza con le dipendenze.

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

    // Come rifiutoEliminazione, per gli inviti: condivisa da invita e da puoInvitare. Le regole che
    // dipendono dall'invitato (esistenza, admin, già partecipante) stanno in invita, perché il bottone
    // del dettaglio non riguarda un invitato in particolare.
    private Optional<RuntimeException> rifiutoInvito(Partita partita, Partecipazione partecipazione,
                                                     LocalDateTime adesso) {
        if (!eCreatore(partita, partecipazione)) {
            return Optional.of(new AccessoNegatoException(MESSAGGIO_SOLO_CREATORE_INVITA));
        }
        // D3: anche una CONFERMATA che ha perso un posto (per esempio un giocatore campo tolto dall'admin)
        if (!STATI_APERTI_AL_CREATORE.contains(partita.getStato())) {
            return Optional.of(new BusinessRuleException(MESSAGGIO_NON_PIU_APERTA));
        }
        if (RegoleTempoPartita.iniziata(partita, adesso)) {
            return Optional.of(new BusinessRuleException(MESSAGGIO_INVITI_CHIUSI));
        }
        if (partita.getPartecipazioni().size() >= POSTI_MAX) {
            return Optional.of(new BusinessRuleException(MESSAGGIO_AL_COMPLETO));
        }
        return Optional.empty();
    }

    // Come rifiutoEliminazione, per il cambio di visibilità: condivisa da cambiaTipo e da puoCambiareTipo (D8)
    private Optional<RuntimeException> rifiutoCambioTipo(Partita partita, Partecipazione partecipazione,
                                                         LocalDateTime adesso) {
        if (!eCreatore(partita, partecipazione)) {
            return Optional.of(new AccessoNegatoException(MESSAGGIO_SOLO_CREATORE_TIPO));
        }
        if (!STATI_APERTI_AL_CREATORE.contains(partita.getStato())) {
            return Optional.of(new BusinessRuleException(MESSAGGIO_NON_PIU_APERTA));
        }
        if (RegoleTempoPartita.iniziata(partita, adesso)) {
            return Optional.of(new BusinessRuleException(MESSAGGIO_GIA_INIZIATA));
        }
        return Optional.empty();
    }

    // Come rifiutoEliminazione, per la conferma: condivisa da conferma e da puoConfermare.
    // RF_GC_5: partecipazione è sempre un posto UTENTE (trovaPartecipazioneUtente), quindi i posti
    // CAMPO non confermano mai. Una partita già CONFERMATA ha tutti gli utenti confermati e dà
    // "Hai già confermato" prima del controllo sullo stato.
    private Optional<RuntimeException> rifiutoConferma(Partita partita, Partecipazione partecipazione,
                                                       LocalDateTime adesso) {
        if (partecipazione == null) {
            return Optional.of(new BusinessRuleException(MESSAGGIO_NON_PARTECIPI));
        }
        if (Boolean.TRUE.equals(partecipazione.getConfermata())) {
            return Optional.of(new BusinessRuleException(MESSAGGIO_GIA_CONFERMATO));
        }
        if (partita.getStato() != StatoPartita.CREATA) {
            return Optional.of(new BusinessRuleException(MESSAGGIO_NON_PIU_APERTA));
        }
        if (!RegoleTempoPartita.inFinestraConferma(partita, adesso)) {
            return Optional.of(new BusinessRuleException(MESSAGGIO_CONFERMA_FUORI_FINESTRA));
        }
        return Optional.empty();
    }

    // Come rifiutoEliminazione, per il risultato: condivisa da registraRisultato e da puoRegistrareRisultato.
    // partecipazione è sempre un posto UTENTE (trovaPartecipazioneUtente), quindi i posti CAMPO non
    // registrano. Una partita GIOCATA dà "già registrato" prima del controllo sullo stato, come in rifiutoConferma.
    private Optional<RuntimeException> rifiutoRisultato(Partita partita, Partecipazione partecipazione,
                                                        LocalDateTime adesso) {
        if (partecipazione == null) {
            return Optional.of(new BusinessRuleException(MESSAGGIO_NON_PARTECIPI));
        }
        if (partita.getStato() == StatoPartita.GIOCATA || RegoleRisultatoPartita.risultatoRegistrato(partita)) {
            return Optional.of(new BusinessRuleException(MESSAGGIO_RISULTATO_GIA_REGISTRATO));
        }
        if (partita.getStato() != StatoPartita.CONFERMATA) {
            return Optional.of(new BusinessRuleException(MESSAGGIO_RISULTATO_SOLO_CONFERMATE));
        }
        if (!RegoleTempoPartita.finita(partita, adesso)) {
            return Optional.of(new BusinessRuleException(MESSAGGIO_PARTITA_NON_FINITA));
        }
        if (!RegoleTempoPartita.inFinestraRisultato(partita, adesso)) {
            return Optional.of(new BusinessRuleException(MESSAGGIO_TERMINE_RISULTATO_SCADUTO));
        }
        return Optional.empty();
    }

    // RF_GR_3: due giocatori per squadra e, insieme, esattamente i 4 posti della partita, giocatori
    // campo compresi. Il confronto tra insiemi scarta sia gli id ripetuti sia quelli di altre partite;
    // il controllo su POSTI_MAX serve se la partita ha meno di 4 posti e un id è ripetuto.
    private void verificaSquadre(Partita partita, List<Integer> squadra1, List<Integer> squadra2) {
        if (squadra1 == null || squadra2 == null
                || squadra1.size() != GIOCATORI_PER_SQUADRA || squadra2.size() != GIOCATORI_PER_SQUADRA) {
            throw new RichiestaNonValidaException(MESSAGGIO_GIOCATORI_PER_SQUADRA);
        }
        Set<Integer> inseriti = new HashSet<>(squadra1);
        inseriti.addAll(squadra2);
        Set<Integer> postiPartita = partita.getPartecipazioni().stream()
                .map(Partecipazione::getId)
                .collect(Collectors.toSet());
        if (inseriti.size() != POSTI_MAX || !inseriti.equals(postiPartita)) {
            throw new RichiestaNonValidaException(MESSAGGIO_SQUADRE_POSTI);
        }
    }

    // Una lista o un set mancanti nel JSON arrivano come null: li giudica RegoleRisultatoPartita
    private List<RegoleRisultatoPartita.SetInserito> setInseriti(List<PartitaDTO.SetRequest> set) {
        if (set == null) {
            return null;
        }
        List<RegoleRisultatoPartita.SetInserito> inseriti = new ArrayList<>();
        for (PartitaDTO.SetRequest s : set) {
            inseriti.add(s == null
                    ? new RegoleRisultatoPartita.SetInserito(null, null)
                    : new RegoleRisultatoPartita.SetInserito(s.getSquadra1(), s.getSquadra2()));
        }
        return inseriti;
    }

    // Con 2 set le colonne del terzo sono NULL, come richiede il vincolo ck_partita_set: vanno azzerate
    // anche qui, perché una correzione dell'admin può passare da 3 set a 2
    private void scriviSet(Partita partita, List<RegoleRisultatoPartita.SetInserito> set) {
        partita.setSet1Sq1(set.get(0).squadra1());
        partita.setSet1Sq2(set.get(0).squadra2());
        partita.setSet2Sq1(set.get(1).squadra1());
        partita.setSet2Sq2(set.get(1).squadra2());
        if (set.size() > 2) {
            partita.setSet3Sq1(set.get(2).squadra1());
            partita.setSet3Sq2(set.get(2).squadra2());
        } else {
            partita.setSet3Sq1(null);
            partita.setSet3Sq2(null);
        }
    }

    // Storico e "Le mie partite" (R12): una sola query, partita e campo arrivano in JOIN FETCH con la
    // partecipazione dell'utente, che porta già squadra e punti; le altre partecipazioni non servono.
    // La divisione sulla fine effettiva si fa in Java, come per le partite dopo mezzanotte nella bacheca
    private List<Partecipazione> partecipazioniNonAnnullate(String telefono) {
        return partecipazioneRepository.findStoricoByTelefono(telefono, TipoPartecipazione.UTENTE,
                StatoPartita.ANNULLATA);
    }

    // Solo i due valori del contratto, come in AdminService: il messaggio è senza il prefisso del campo
    private TipoPartita tipoDaRichiesta(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            throw new RichiestaNonValidaException(MESSAGGIO_TIPO_OBBLIGATORIO);
        }
        for (TipoPartita valore : TipoPartita.values()) {
            if (valore.name().equals(tipo)) {
                return valore;
            }
        }
        throw new RichiestaNonValidaException(MESSAGGIO_TIPO_NON_VALIDO);
    }

    // Pattern per findInvitabili: "contiene", senza distinzione tra maiuscole e minuscole.
    // Senza testo di ricerca diventa "%%", che corrisponde a tutti (nome e cognome non sono mai null).
    // %, _ e il carattere di escape scritti dall'utente vanno cercati come caratteri normali.
    static String patternRicerca(String cerca) {
        String testo = cerca == null ? "" : cerca.trim().toLowerCase(Locale.ROOT);
        StringBuilder pattern = new StringBuilder("%");
        for (char c : testo.toCharArray()) {
            if (c == '%' || c == '_' || c == ESCAPE_LIKE) {
                pattern.append(ESCAPE_LIKE);
            }
            pattern.append(c);
        }
        return pattern.append('%').toString();
    }

    // Creatore = il richiedente occupa il posto UTENTE del creatore. Un admin creatore non occupa un posto
    // (vedi AdminService.modificaPartecipanti): in quel caso nessun partecipante è il creatore, quindi
    // nessuno invita o elimina e tutti possono uscire.
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
        if (creatoreAdmin(creatore)) {
            return true;
        }
        return Math.abs(richiedente.getLivello() - creatore.getLivello()) <= DIFFERENZA_LIVELLO_MAX;
    }

    private boolean creatoreAdmin(Utente creatore) {
        return Boolean.TRUE.equals(creatore.getIsAdmin());
    }

    // Cerca il nome del vincolo nella catena delle cause, come in PrenotazioneService
    static boolean violaVincolo(Throwable ex, String nomeVincolo) {
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
        boolean puoInvitare = rifiutoInvito(partita, partecipazione, adesso).isEmpty();
        boolean puoEliminare = rifiutoEliminazione(partita, partecipazione, adesso).isEmpty();
        boolean puoUscire = rifiutoUscita(partita, partecipazione, adesso).isEmpty();
        boolean puoConfermare = rifiutoConferma(partita, partecipazione, adesso).isEmpty();
        boolean puoRegistrareRisultato = rifiutoRisultato(partita, partecipazione, adesso).isEmpty();
        boolean puoCambiareTipo = rifiutoCambioTipo(partita, partecipazione, adesso).isEmpty();

        return new PartitaDTO.AzioniResponse(puoInvitare, puoEliminare, puoUscire, puoConfermare, puoRegistrareRisultato,
                puoCambiareTipo);
    }

    // Partecipazione UTENTE del richiedente, se occupa un posto nella partita
    private Optional<Partecipazione> trovaPartecipazioneUtente(Partita partita, String telefono) {
        return partita.getPartecipazioni().stream()
                .filter(pa -> pa.getTipo() == TipoPartecipazione.UTENTE)
                .filter(pa -> pa.getUtente().getTelefono().equals(telefono))
                .findFirst();
    }

    static ResourceNotFoundException partitaNonTrovata(Integer idPartita) {
        return new ResourceNotFoundException("Partita non trovata con id " + idPartita);
    }

    // Il creatore per primo, poi gli altri posti in ordine di id (@OrderBy su Partita.partecipazioni):
    // l'ordinamento è stabile, quindi sposta solo il creatore, e con un admin creatore, che non ha un posto,
    // resta l'ordine di id. delRichiedente è il posto di chi guarda, null
    // per l'admin e per chi non gioca: serve a vedere il risultato dal suo punto di vista.
    private PartitaDTO.DettaglioResponse toDettaglioResponse(Partita p, PartitaDTO.AzioniResponse azioni,
                                                             Partecipazione delRichiedente) {
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
                azioni,
                toRisultatoResponse(p, delRichiedente)
        );
    }

    private boolean eDelCreatore(Partecipazione pa, String telefonoCreatore) {
        return pa.getTipo() == TipoPartecipazione.UTENTE && pa.getUtente().getTelefono().equals(telefonoCreatore);
    }

    private PartitaDTO.PostoResponse toPostoResponse(Partecipazione pa) {
        Utente u = pa.getUtente();
        if (pa.getTipo() == TipoPartecipazione.CAMPO || u == null) {
            return new PartitaDTO.PostoResponse(pa.getId(), pa.getTipo().name(), null, null, null, null,
                    pa.getConfermata(), pa.getSquadra());
        }
        return new PartitaDTO.PostoResponse(pa.getId(), pa.getTipo().name(), u.getTelefono(), u.getNome(),
                u.getCognome(), u.getLivello(), pa.getConfermata(), pa.getSquadra());
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
                toRisultatoResponse(p, pa)
        );
    }

    // null finché il risultato non è registrato. Il vincitore lo stabilisce RegoleRisultatoPartita;
    // l'esito resta null se l'utente non ha una squadra o se i set non indicano un vincitore.
    // pa è il posto di chi guarda, null per l'admin e per chi non gioca, che non hanno né squadra né punti.
    private PartitaDTO.RisultatoResponse toRisultatoResponse(Partita p, Partecipazione pa) {
        if (!RegoleRisultatoPartita.risultatoRegistrato(p)) {
            return null;
        }
        List<RegoleRisultatoPartita.SetGiocato> setGiocati = RegoleRisultatoPartita.setGiocati(p);
        List<PartitaDTO.SetResponse> set = setGiocati.stream()
                .map(s -> new PartitaDTO.SetResponse(s.squadra1(), s.squadra2()))
                .toList();

        Integer squadraUtente = pa != null ? pa.getSquadra() : null;
        Integer vincitrice = RegoleRisultatoPartita.squadraVincitrice(setGiocati);
        String esito = null;
        if (squadraUtente != null && vincitrice != null) {
            esito = squadraUtente.equals(vincitrice) ? ESITO_VINTA : ESITO_PERSA;
        }
        return new PartitaDTO.RisultatoResponse(set, squadraUtente, esito, pa != null ? pa.getPuntiAssegnati() : null);
    }

    private PartitaDTO.InvitoRicevutoResponse toInvitoRicevutoResponse(Partita p) {
        Utente creatore = p.getCreatore();
        return new PartitaDTO.InvitoRicevutoResponse(
                p.getId(),
                p.getCampo().getId(),
                p.getCampo().getNome(),
                p.getDataPartita(),
                p.getOraInizio(),
                p.getOraFine(),
                p.getStato().name().toLowerCase(Locale.ROOT),
                p.getTipo().name(),
                creatore.getNome(),
                creatore.getCognome()
        );
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
                // L'admin non ha un livello di gioco: il client non lo mostra
                creatoreAdmin(creatore) ? null : creatore.getLivello(),
                POSTI_MAX - p.getPartecipazioni().size()
        );
    }
}
