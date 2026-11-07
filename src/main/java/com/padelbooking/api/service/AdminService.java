package com.padelbooking.api.service;

import com.padelbooking.api.dto.AdminDTO;
import com.padelbooking.api.dto.PartitaDTO;
import com.padelbooking.api.exception.BusinessRuleException;
import com.padelbooking.api.exception.ResourceNotFoundException;
import com.padelbooking.api.exception.RichiestaNonValidaException;
import com.padelbooking.api.model.Avviso;
import com.padelbooking.api.model.Campo;
import com.padelbooking.api.model.Impostazioni;
import com.padelbooking.api.model.Partecipazione;
import com.padelbooking.api.model.Partita;
import com.padelbooking.api.model.StatoPartita;
import com.padelbooking.api.model.TipoPartecipazione;
import com.padelbooking.api.model.TipoPartita;
import com.padelbooking.api.model.Utente;
import com.padelbooking.api.repository.AvvisoRepository;
import com.padelbooking.api.repository.CampoRepository;
import com.padelbooking.api.repository.ImpostazioniRepository;
import com.padelbooking.api.repository.PartitaRepository;
import com.padelbooking.api.repository.UtenteRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// Area amministratore (UC_GA_1–7, UC_GU_3): le regole dei giocatori non valgono, in particolare i limiti
// di tempo. L'accesso riservato all'admin sta in SecurityConfig. Le scritture sulla partita leggono con il
// lock come prima lettura della transazione, per il motivo spiegato in PartitaService.entra.
@Service
public class AdminService {

    // Una partita ha al massimo 4 posti (UTENTE o CAMPO): la soglia sta in RegoleConfermaPartita
    private static final int POSTI_MAX = RegoleConfermaPartita.getPostiMax();

    // RF_GU_6: i livelli vanno da 0 a 5 con passo 0,5 (vincolo ck_utente_livello)
    private static final double LIVELLO_MIN = 0.0;
    private static final double LIVELLO_MAX = 5.0;
    private static final double PASSI_PER_LIVELLO = 2.0;

    // Quanti giocatori campo si possono aggiungere con una richiesta: un posto è sempre di un utente
    private static final int GIOCATORI_CAMPO_MIN = 1;
    private static final int GIOCATORI_CAMPO_MAX = 3;

    // Una partita iniziata non ha più un avviso aperto. L'inizio effettivo cade al più il giorno dopo
    // data_partita, quindi una partita non ancora iniziata ha data_partita da ieri in poi
    private static final int GIORNI_INDIETRO_AVVISI = 1;

    // Vincolo unico sugli slot attivi: la colonna generata slot_attivo vale NULL solo per le annullate
    private static final String VINCOLO_SLOT_UNICO = "uk_partita_slot";

    private static final String NOME_GIOCATORE_CAMPO = "Giocatore del campo";
    // T18: il posto di chi ha eliminato l'account, che resta nelle partite già finite o annullate
    private static final String NOME_UTENTE_ELIMINATO = "Utente eliminato";

    // Messaggi (RNF_U_3): fanno parte del contratto con il client
    private static final String MESSAGGIO_LIVELLO_NON_VALIDO = "Il livello deve essere tra 0 e 5 con passo 0,5";
    private static final String MESSAGGIO_UTENTE_NON_TROVATO = "Utente non trovato";
    private static final String MESSAGGIO_ALMENO_UN_UTENTE = "Almeno un giocatore deve essere un utente";
    private static final String MESSAGGIO_MASSIMO_GIOCATORI = "Una partita può avere al massimo 4 giocatori";
    private static final String MESSAGGIO_TELEFONI_DUPLICATI = "L'elenco dei partecipanti contiene telefoni duplicati";
    private static final String MESSAGGIO_PARTECIPANTI_NON_MODIFICABILI =
            "Si possono modificare solo le partite non annullate e non giocate";
    private static final String MESSAGGIO_ADMIN_NON_PARTECIPA = "Un amministratore non può partecipare";
    private static final String MESSAGGIO_RISULTATO_NON_MODIFICABILE =
            "Il risultato si può modificare solo per le partite confermate o giocate";
    private static final String MESSAGGIO_STATO_NON_VALIDO = "Stato non valido";
    private static final String MESSAGGIO_SLOT_OCCUPATO = "Lo slot è già occupato da un'altra partita";
    private static final String MESSAGGIO_GIA_ANNULLATA = "La partita è già annullata";
    private static final String MESSAGGIO_NUMERO_NON_VALIDO = "Il numero deve essere tra 1 e 3";
    private static final String MESSAGGIO_SERVE_UN_UTENTE = "Serve almeno un giocatore utente";
    private static final String MESSAGGIO_POSTO_NON_TROVATO = "Giocatore del campo non trovato";
    private static final String MESSAGGIO_GIOCATORI_CAMPO_NEGATIVI = "Il numero di giocatori del campo non può essere negativo";
    private static final String MESSAGGIO_CAMPO_OBBLIGATORIO = "Il campo è obbligatorio";
    private static final String MESSAGGIO_DATA_OBBLIGATORIA = "La data è obbligatoria";
    private static final String MESSAGGIO_ORA_OBBLIGATORIA = "L'ora di inizio è obbligatoria";
    private static final String MESSAGGIO_TIPO_OBBLIGATORIO = "Il tipo è obbligatorio";
    private static final String MESSAGGIO_TIPO_NON_VALIDO = "Il tipo deve essere PUBBLICA o PRIVATA";

    private final PartitaRepository partitaRepository;
    private final CampoRepository campoRepository;
    private final AvvisoRepository avvisoRepository;
    private final UtenteRepository utenteRepository;
    private final PartitaService partitaService;
    private final PunteggiService punteggiService;
    private final PrenotazioneService prenotazioneService;
    private final ImpostazioniRepository impostazioniRepository;
    private final EntityManager entityManager;
    private final Clock clock;

    public AdminService(PartitaRepository partitaRepository,
                        CampoRepository campoRepository,
                        AvvisoRepository avvisoRepository,
                        UtenteRepository utenteRepository,
                        PartitaService partitaService,
                        PunteggiService punteggiService,
                        PrenotazioneService prenotazioneService,
                        ImpostazioniRepository impostazioniRepository,
                        EntityManager entityManager,
                        Clock clock) {
        this.partitaRepository = partitaRepository;
        this.campoRepository = campoRepository;
        this.avvisoRepository = avvisoRepository;
        this.utenteRepository = utenteRepository;
        this.partitaService = partitaService;
        this.punteggiService = punteggiService;
        this.prenotazioneService = prenotazioneService;
        this.impostazioniRepository = impostazioniRepository;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    // ============================================
    // Partite del giorno
    // ============================================
    // UC_GA_1, RF_GA_1–2: un elemento per ogni campo, anche senza partite. Le partite sono in ordine di inizio
    // effettivo, così quella delle 00:30 della serata viene dopo quella delle 18:00. A parità di inizio, per
    // esempio una partita annullata e quella che ne ha preso lo slot, l'id rende l'ordine stabile.
    @Transactional(readOnly = true)
    public List<AdminDTO.CampoPartiteResponse> getPartiteDelGiorno(LocalDate data) {
        Map<Integer, List<Partita>> partitePerCampo = partitaRepository.findByDataConPartecipanti(data).stream()
                .collect(Collectors.groupingBy(p -> p.getCampo().getId()));

        return campoRepository.findAll(Sort.by("nome", "id")).stream()
                .map(campo -> toCampoPartiteResponse(campo, partitePerCampo.getOrDefault(campo.getId(), List.of())))
                .toList();
    }

    // ============================================
    // Bacheca avvisi
    // ============================================
    // UC_GA_6, RF_GA_7–9: l'avviso resta fino all'ora di inizio. Il filtro preciso e l'ordinamento si fanno
    // sull'inizio effettivo, come nella bacheca pubbliche, per le partite dopo mezzanotte.
    // Una partita con tutti i posti occupati non ha bisogno dell'avviso. La riga però resta: se si riapre un
    // posto l'avviso torna visibile, e lo scheduler non ne crea un altro (vincolo uk_avviso).
    @Transactional(readOnly = true)
    public List<AdminDTO.AvvisoResponse> getAvvisi() {
        LocalDateTime adesso = LocalDateTime.now(clock);
        return avvisoRepository.findAperti(StatoPartita.ANNULLATA, adesso.toLocalDate().minusDays(GIORNI_INDIETRO_AVVISI))
                .stream()
                .filter(a -> !RegoleTempoPartita.iniziata(a.getPartita(), adesso))
                .filter(a -> !RegoleConfermaPartita.alCompleto(a.getPartita()))
                .sorted(Comparator.comparing((Avviso a) -> RegoleTempoPartita.inizioEffettivo(a.getPartita()))
                        .thenComparing(Avviso::getId))
                .map(this::toAvvisoResponse)
                .toList();
    }

    // ============================================
    // Gestione livelli
    // ============================================
    // UC_GU_3: la ricerca funziona come quella degli utenti invitabili
    @Transactional(readOnly = true)
    public List<AdminDTO.UtenteResponse> getUtenti(String cerca) {
        return utenteRepository.findNonAdmin(PartitaService.patternRicerca(cerca)).stream()
                .map(this::toUtenteResponse)
                .toList();
    }

    // RF_GU_6–7. Hibernate scrive tutte le colonne di utente: senza il lock con refresh, un punteggio
    // aggiornato da un risultato registrato in parallelo verrebbe sovrascritto con quello letto prima
    // (vedi PunteggiService.bloccaUtenti).
    @Transactional
    public AdminDTO.UtenteResponse modificaLivello(String telefono, Double livello) {
        if (!livelloValido(livello)) {
            throw new RichiestaNonValidaException(MESSAGGIO_LIVELLO_NON_VALIDO);
        }
        // Un admin non ha un livello da gestire: per questa pagina è come se non esistesse
        Utente utente = utenteRepository.findById(telefono)
                .filter(u -> !Boolean.TRUE.equals(u.getIsAdmin()))
                .orElseThrow(() -> new ResourceNotFoundException(MESSAGGIO_UTENTE_NON_TROVATO));

        entityManager.refresh(utente, LockModeType.PESSIMISTIC_WRITE);
        utente.setLivello(livello);
        return toUtenteResponse(utente);
    }

    // ============================================
    // Modifica dei partecipanti
    // ============================================
    // UC_GA_2, RF_GA_3, RF_GA_12: telefoni è l'elenco finale dei posti UTENTE, senza limiti di tempo.
    // Si toccano solo le differenze: chi resta conserva i suoi dati e nessun utente esce e rientra nella
    // stessa richiesta. Per questo l'ordine di Hibernate, che fa gli INSERT prima dei DELETE, non può
    // violare uk_partecipazione_utente.
    // Se l'elenco non contiene il creatore, il creatore diventa l'admin che fa la richiesta: l'admin non occupa
    // un posto. Chi viene riaggiunto dopo è un partecipante normale. Un creatore null (account eliminato, T18)
    // vale già come admin e resta com'è.
    // I posti degli utenti eliminati (T18), possibili in una partita finita ma non ancora giocata, non hanno
    // telefono: non si possono indicare in telefoni, quindi restano e occupano un posto come i giocatori campo.
    @Transactional
    public PartitaDTO.DettaglioResponse modificaPartecipanti(Integer idPartita, List<String> telefoni,
                                                             String telefonoAdmin) {
        if (telefoni == null || telefoni.isEmpty()) {
            throw new RichiestaNonValidaException(MESSAGGIO_ALMENO_UN_UTENTE);
        }
        Set<String> telefoniFinali = new LinkedHashSet<>(telefoni);
        if (telefoniFinali.size() != telefoni.size()) {
            throw new RichiestaNonValidaException(MESSAGGIO_TELEFONI_DUPLICATI);
        }

        Partita partita = trovaConLockOLancia(idPartita);
        verificaPostiModificabili(partita);
        long postiSenzaUtente = partita.getPartecipazioni().stream()
                .filter(pa -> pa.getTipo() == TipoPartecipazione.CAMPO || pa.utenteEliminato())
                .count();
        if (telefoniFinali.size() + postiSenzaUtente > POSTI_MAX) {
            throw new RichiestaNonValidaException(MESSAGGIO_MASSIMO_GIOCATORI);
        }

        List<Partecipazione> postiUtente = partita.getPartecipazioni().stream()
                .filter(pa -> pa.getTipo() == TipoPartecipazione.UTENTE && !pa.utenteEliminato())
                .toList();
        Set<String> telefoniAttuali = postiUtente.stream()
                .map(pa -> pa.getUtente().getTelefono())
                .collect(Collectors.toSet());
        // Prima tutti i controlli, poi le modifiche
        List<Utente> nuovi = new ArrayList<>();
        for (String telefono : telefoniFinali) {
            if (!telefoniAttuali.contains(telefono)) {
                nuovi.add(trovaPartecipanteOLancia(telefono));
            }
        }
        // Ogni INSERT prende un lock condiviso sulla riga utente per la chiave esterna: in ordine di telefono,
        // come i lock di PunteggiService, per non andare in deadlock con un risultato registrato in parallelo
        nuovi.sort(Comparator.comparing(Utente::getTelefono));

        if (partita.getCreatore() != null && !telefoniFinali.contains(partita.getCreatore().getTelefono())) {
            // L'admin ha autenticato la richiesta, quindi esiste: basta il riferimento per la chiave esterna
            partita.setCreatore(utenteRepository.getReferenceById(telefonoAdmin));
        }

        // postiUtente è una copia: si può togliere dalla lista della partita mentre la si scorre
        for (Partecipazione posto : postiUtente) {
            if (!telefoniFinali.contains(posto.getUtente().getTelefono())) {
                partita.rimuoviPartecipazione(posto);
            }
        }
        // In una partita già confermata nessuno deve più confermare, e da −12 h non si potrebbe più (D2):
        // in entrambi i casi i nuovi entrano confermati. Si calcola prima di aggiungerli, sullo stato di adesso
        boolean confermata = RegoleConfermaPartita.confermataAllIngresso(partita, LocalDateTime.now(clock));
        for (Utente utente : nuovi) {
            partita.aggiungiPartecipazione(new Partecipazione(TipoPartecipazione.UTENTE, utente, false, confermata));
        }

        // RF_GC_7
        if (RegoleConfermaPartita.confermaSeCompleta(partita)) {
            // T13: notifica ADMIN_CONFERMA all'amministratore (RF_GN_8)
        }

        // Come in PartitaService.entra: il flush assegna gli id ai nuovi posti, che vanno nella risposta
        partitaRepository.flush();

        return partitaService.dettaglioPerAdmin(partita);
    }

    // ============================================
    // Modifica del risultato
    // ============================================
    // UC_GA_3, RF_GA_4, RF_GCL_12–13: correzione di una partita giocata o registrazione al posto dei
    // giocatori, senza limiti di tempo. Tutto nella stessa transazione: un errore annulla anche lo storno.
    @Transactional
    public PartitaDTO.DettaglioResponse modificaRisultato(Integer idPartita, PartitaDTO.RisultatoRequest request,
                                                          String telefonoAdmin) {
        Partita partita = trovaConLockOLancia(idPartita);
        if (partita.getStato() != StatoPartita.CONFERMATA && partita.getStato() != StatoPartita.GIOCATA) {
            throw new BusinessRuleException(MESSAGGIO_RISULTATO_NON_MODIFICABILE);
        }
        partitaService.validaRisultato(partita, request);

        // Lo storno si salta da solo per i posti senza punti, come in una partita ancora da registrare
        punteggiService.stornaPunti(partita);
        // L'admin ha autenticato la richiesta, quindi esiste: basta il riferimento per la chiave esterna
        partitaService.scriviRisultato(partita, request, utenteRepository.getReferenceById(telefonoAdmin),
                LocalDateTime.now(clock));
        punteggiService.assegnaPunti(partita);

        return partitaService.dettaglioPerAdmin(partita);
    }

    // ============================================
    // Modifica dello stato
    // ============================================
    // UC_GA_4, RF_GA_5, RF_GCL_14: qualunque transizione è ammessa. I punti esistono solo sulle partite
    // GIOCATA: chi esce da GIOCATA li perde, verso qualunque stato, altrimenti resterebbero su una partita che
    // lo scheduler può annullare o a cui si possono togliere giocatori. Chi entra li riceve con i livelli attuali.
    // Il passaggio a GIOCATA resta permesso anche se i punti non si possono calcolare, come senza risultato.
    @Transactional
    public PartitaDTO.DettaglioResponse modificaStato(Integer idPartita, String stato) {
        StatoPartita nuovo = statoDaRichiesta(stato);

        Partita partita = trovaConLockOLancia(idPartita);
        StatoPartita precedente = partita.getStato();
        // Da GIOCATA a GIOCATA non cambia nulla: nessuno storno e nessun nuovo conteggio
        if (precedente == StatoPartita.GIOCATA && nuovo != StatoPartita.GIOCATA) {
            punteggiService.stornaPunti(partita);
        }
        partita.setStato(nuovo);

        try {
            // Flush esplicito: da annullata a un altro stato slot_attivo torna valorizzata e il vincolo
            // può scattare. Al commit l'errore arriverebbe fuori da qui e diventerebbe un 500.
            partitaRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            if (PartitaService.violaVincolo(ex, VINCOLO_SLOT_UNICO)) {
                throw new BusinessRuleException(MESSAGGIO_SLOT_OCCUPATO);
            }
            throw ex;
        }

        // Dopo il flush: se lo slot è occupato non si bloccano gli utenti per niente
        if (precedente != StatoPartita.GIOCATA && nuovo == StatoPartita.GIOCATA
                && RegoleRisultatoPartita.puntiCalcolabili(partita)) {
            punteggiService.assegnaPunti(partita);
        }

        if (nuovo == StatoPartita.ANNULLATA && precedente != StatoPartita.ANNULLATA) {
            // T13: notifiche di annullamento (RF_GN_3, RF_GN_7)
        }

        return partitaService.dettaglioPerAdmin(partita);
    }

    // ============================================
    // Annullamento
    // ============================================
    // UC_GA_5, RF_GA_6, RF_GCL_14: in qualsiasi momento. Con ANNULLATA la colonna generata slot_attivo
    // diventa NULL e lo slot torna prenotabile.
    @Transactional
    public PartitaDTO.DettaglioResponse annulla(Integer idPartita) {
        Partita partita = trovaConLockOLancia(idPartita);
        if (partita.getStato() == StatoPartita.ANNULLATA) {
            throw new BusinessRuleException(MESSAGGIO_GIA_ANNULLATA);
        }
        if (partita.getStato() == StatoPartita.GIOCATA) {
            punteggiService.stornaPunti(partita);
        }
        // La partita è gestita dall'EntityManager: il cambio di stato lo scrive il dirty checking al commit
        partita.setStato(StatoPartita.ANNULLATA);

        // T13: notifiche di annullamento (RF_GN_3, RF_GN_7)

        return partitaService.dettaglioPerAdmin(partita);
    }

    // ============================================
    // Impostazioni
    // ============================================
    @Transactional(readOnly = true)
    public AdminDTO.ImpostazioniResponse getImpostazioni() {
        return new AdminDTO.ImpostazioniResponse(trovaImpostazioni().getLivelloGiocatoriCampo());
    }

    // RF_GA_14–15: vale per i calcoli successivi. I punti già assegnati non si ricalcolano, e uno storno
    // toglie quello che è scritto in punti_assegnati, indipendentemente dal livello di adesso.
    @Transactional
    public AdminDTO.ImpostazioniResponse modificaLivelloGiocatoriCampo(Double livello) {
        if (!livelloValido(livello)) {
            throw new RichiestaNonValidaException(MESSAGGIO_LIVELLO_NON_VALIDO);
        }
        Impostazioni impostazioni = trovaImpostazioni();
        impostazioni.setLivelloGiocatoriCampo(livello);
        return new AdminDTO.ImpostazioniResponse(livello);
    }

    // ============================================
    // Giocatori campo
    // ============================================
    // UC_GA_7, RF_GA_10–13, RF_GC_5, RF_GC_7: senza limiti di tempo, quindi la partita può completarsi e
    // passare a CONFERMATA anche dopo le −12 h, se tutti gli utenti avevano già confermato.
    @Transactional
    public PartitaDTO.DettaglioResponse aggiungiGiocatoriCampo(Integer idPartita, Integer numero) {
        if (numero == null || numero < GIOCATORI_CAMPO_MIN || numero > GIOCATORI_CAMPO_MAX) {
            throw new RichiestaNonValidaException(MESSAGGIO_NUMERO_NON_VALIDO);
        }

        Partita partita = trovaConLockOLancia(idPartita);
        verificaPostiModificabili(partita);
        int postiLiberi = POSTI_MAX - partita.getPartecipazioni().size();
        if (numero > postiLiberi) {
            throw new BusinessRuleException("Puoi aggiungere al massimo " + postiLiberi
                    + (postiLiberi == 1 ? " giocatore del campo" : " giocatori del campo"));
        }
        if (!RegoleConfermaPartita.haGiocatoriUtente(partita)) {
            throw new BusinessRuleException(MESSAGGIO_SERVE_UN_UTENTE);
        }

        aggiungiPostiCampo(partita, numero);

        // RF_GC_7
        if (RegoleConfermaPartita.confermaSeCompleta(partita)) {
            // T13: notifica ADMIN_CONFERMA all'amministratore (RF_GN_8)
        }

        // Come in modificaPartecipanti: il flush assegna gli id ai nuovi posti, che vanno nella risposta
        partitaRepository.flush();

        return partitaService.dettaglioPerAdmin(partita);
    }

    // RF_GA_3: lo stato non cambia. Se una CONFERMATA resta sotto i 4 posti, all'inizio la annulla lo
    // scheduler (RF_GC_10). Gli utenti restano tutti, quindi resta anche il giocatore utente obbligatorio.
    @Transactional
    public PartitaDTO.DettaglioResponse rimuoviGiocatoreCampo(Integer idPartita, Integer idPosto) {
        Partita partita = trovaConLockOLancia(idPartita);
        verificaPostiModificabili(partita);
        Partecipazione posto = partita.getPartecipazioni().stream()
                .filter(pa -> pa.getTipo() == TipoPartecipazione.CAMPO && pa.getId().equals(idPosto))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(MESSAGGIO_POSTO_NON_TROVATO));

        partita.rimuoviPartecipazione(posto);

        return partitaService.dettaglioPerAdmin(partita);
    }

    // ============================================
    // Creazione di una partita
    // ============================================
    // L'admin è il creatore ma non un giocatore: non occupa un posto, e se vuole giocare inserisce un
    // giocatore campo. Le regole di slot, compreso il limite dei 15 giorni, sono quelle di POST /api/prenotazioni
    // e stanno in PrenotazioneService. Lo stato è CREATA anche con 4 posti, perché gli utenti aggiunti devono
    // ancora confermare, tranne a meno di 12 ore dall'inizio (D2): lì nascono confermati e con 4 posti la
    // partita nasce CONFERMATA.
    @Transactional
    public PartitaDTO.DettaglioResponse creaPartita(AdminDTO.NuovaPartitaRequest request, String telefonoAdmin) {
        TipoPartita tipo = validaNuovaPartita(request);
        int giocatoriCampo = request.getGiocatoriCampo() == null ? 0 : request.getGiocatoriCampo();

        Partita partita = prenotazioneService.nuovaPartitaSuSlot(request.getIdCampo(), request.getDataPartita(),
                request.getOraInizio());

        List<Utente> giocatori = new ArrayList<>();
        for (String telefono : request.getTelefoni()) {
            giocatori.add(trovaPartecipanteOLancia(telefono));
        }
        // Stessa regola di modificaPartecipanti: gli INSERT prendono il lock sulla riga utente in ordine di telefono
        giocatori.sort(Comparator.comparing(Utente::getTelefono));

        // L'admin ha autenticato la richiesta, quindi esiste: basta il riferimento per la chiave esterna
        partita.setCreatore(utenteRepository.getReferenceById(telefonoAdmin));
        partita.setTipo(tipo);
        boolean confermata = RegoleConfermaPartita.confermataAllIngresso(partita, LocalDateTime.now(clock));
        for (Utente giocatore : giocatori) {
            partita.aggiungiPartecipazione(new Partecipazione(TipoPartecipazione.UTENTE, giocatore, false, confermata));
        }
        aggiungiPostiCampo(partita, giocatoriCampo);

        // RF_GC_7
        if (RegoleConfermaPartita.confermaSeCompleta(partita)) {
            // T13: notifica ADMIN_CONFERMA all'amministratore (RF_GN_8)
        }

        Partita salvata = prenotazioneService.salvaNuovaPartita(partita);

        // T13: notifiche ai giocatori aggiunti dall'admin

        return partitaService.dettaglioPerAdmin(salvata);
    }

    // ============================================
    // Helper privati
    // ============================================

    private Partita trovaConLockOLancia(Integer idPartita) {
        return partitaRepository.findByIdPerAggiornamento(idPartita)
                .orElseThrow(() -> PartitaService.partitaNonTrovata(idPartita));
    }

    // Un telefono null nel JSON non corrisponde a nessun utente
    private Utente trovaPartecipanteOLancia(String telefono) {
        Utente utente = (telefono == null ? Optional.<Utente>empty() : utenteRepository.findById(telefono))
                .orElseThrow(() -> new ResourceNotFoundException(MESSAGGIO_UTENTE_NON_TROVATO));
        if (Boolean.TRUE.equals(utente.getIsAdmin())) {
            throw new BusinessRuleException(MESSAGGIO_ADMIN_NON_PARTECIPA);
        }
        return utente;
    }

    // Restituisce il tipo già convertito. I posti si contano qui: con almeno un utente, più di 3 giocatori
    // campo superano sempre i 4 posti, quindi basta il messaggio generale.
    private TipoPartita validaNuovaPartita(AdminDTO.NuovaPartitaRequest request) {
        if (request.getIdCampo() == null) {
            throw new RichiestaNonValidaException(MESSAGGIO_CAMPO_OBBLIGATORIO);
        }
        if (request.getDataPartita() == null) {
            throw new RichiestaNonValidaException(MESSAGGIO_DATA_OBBLIGATORIA);
        }
        if (request.getOraInizio() == null) {
            throw new RichiestaNonValidaException(MESSAGGIO_ORA_OBBLIGATORIA);
        }
        TipoPartita tipo = tipoDaRichiesta(request.getTipo());

        List<String> telefoni = request.getTelefoni();
        if (telefoni == null || telefoni.isEmpty()) {
            throw new RichiestaNonValidaException(MESSAGGIO_ALMENO_UN_UTENTE);
        }
        if (new LinkedHashSet<>(telefoni).size() != telefoni.size()) {
            throw new RichiestaNonValidaException(MESSAGGIO_TELEFONI_DUPLICATI);
        }
        int giocatoriCampo = request.getGiocatoriCampo() == null ? 0 : request.getGiocatoriCampo();
        if (giocatoriCampo < 0) {
            throw new RichiestaNonValidaException(MESSAGGIO_GIOCATORI_CAMPO_NEGATIVI);
        }
        // long: la somma non deve poter traboccare con un valore enorme nel JSON
        if ((long) telefoni.size() + giocatoriCampo > POSTI_MAX) {
            throw new RichiestaNonValidaException(MESSAGGIO_MASSIMO_GIOCATORI);
        }
        return tipo;
    }

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

    private void verificaPostiModificabili(Partita partita) {
        StatoPartita stato = partita.getStato();
        if (stato == StatoPartita.ANNULLATA || stato == StatoPartita.GIOCATA) {
            throw new BusinessRuleException(MESSAGGIO_PARTECIPANTI_NON_MODIFICABILI);
        }
    }

    // Il vincolo ck_partecipazione_tipo impone per i posti CAMPO: niente telefono, non invitato, non confermato
    private void aggiungiPostiCampo(Partita partita, int numero) {
        for (int i = 0; i < numero; i++) {
            partita.aggiungiPartecipazione(new Partecipazione(TipoPartecipazione.CAMPO, null, false, false));
        }
    }

    // Senza la riga, creata dallo script SQL, il database non è quello atteso: come in PunteggiService
    private Impostazioni trovaImpostazioni() {
        return impostazioniRepository.findById(PunteggiService.ID_IMPOSTAZIONI)
                .orElseThrow(() -> new IllegalStateException(
                        "Riga " + PunteggiService.ID_IMPOSTAZIONI + " di impostazioni mancante"));
    }

    private boolean livelloValido(Double livello) {
        return livello != null
                && livello >= LIVELLO_MIN && livello <= LIVELLO_MAX
                && livello * PASSI_PER_LIVELLO == Math.floor(livello * PASSI_PER_LIVELLO);
    }

    // Solo i quattro valori minuscoli del contratto, gli stessi che il dettaglio restituisce
    private StatoPartita statoDaRichiesta(String stato) {
        for (StatoPartita valore : StatoPartita.values()) {
            if (valore.name().toLowerCase(Locale.ROOT).equals(stato)) {
                return valore;
            }
        }
        throw new RichiestaNonValidaException(MESSAGGIO_STATO_NON_VALIDO);
    }

    private AdminDTO.CampoPartiteResponse toCampoPartiteResponse(Campo campo, List<Partita> partite) {
        List<AdminDTO.PartitaResponse> ordinate = partite.stream()
                .sorted(Comparator.comparing((Partita p) ->
                                RegoleTempoPartita.inizioEffettivo(campo, p.getDataPartita(), p.getOraInizio()))
                        .thenComparing(Partita::getId))
                .map(this::toPartitaResponse)
                .toList();
        return new AdminDTO.CampoPartiteResponse(campo.getId(), campo.getNome(), ordinate);
    }

    private AdminDTO.PartitaResponse toPartitaResponse(Partita p) {
        List<String> giocatori = p.getPartecipazioni().stream()
                .map(this::nomeGiocatore)
                .toList();
        List<PartitaDTO.SetResponse> risultato = RegoleRisultatoPartita.risultatoRegistrato(p)
                ? RegoleRisultatoPartita.setGiocati(p).stream()
                        .map(s -> new PartitaDTO.SetResponse(s.squadra1(), s.squadra2()))
                        .toList()
                : null;
        return new AdminDTO.PartitaResponse(
                p.getId(),
                p.getOraInizio(),
                p.getOraFine(),
                p.getStato().name().toLowerCase(Locale.ROOT),
                p.getTipo().name(),
                giocatori,
                POSTI_MAX - p.getPartecipazioni().size(),
                risultato
        );
    }

    private String nomeGiocatore(Partecipazione pa) {
        if (pa.utenteEliminato()) {
            return NOME_UTENTE_ELIMINATO;
        }
        if (pa.getTipo() == TipoPartecipazione.CAMPO || pa.getUtente() == null) {
            return NOME_GIOCATORE_CAMPO;
        }
        return pa.getUtente().getNome() + " " + pa.getUtente().getCognome();
    }

    private AdminDTO.AvvisoResponse toAvvisoResponse(Avviso a) {
        Partita p = a.getPartita();
        return new AdminDTO.AvvisoResponse(
                a.getId(),
                p.getId(),
                p.getCampo().getNome(),
                p.getDataPartita(),
                p.getOraInizio(),
                p.getOraFine(),
                POSTI_MAX - p.getPartecipazioni().size()
        );
    }

    private AdminDTO.UtenteResponse toUtenteResponse(Utente u) {
        return new AdminDTO.UtenteResponse(u.getTelefono(), u.getNome(), u.getCognome(), u.getLivello(),
                u.getPunteggio());
    }
}
