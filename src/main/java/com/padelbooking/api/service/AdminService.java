package com.padelbooking.api.service;

import com.padelbooking.api.dto.AdminDTO;
import com.padelbooking.api.dto.PartitaDTO;
import com.padelbooking.api.exception.BusinessRuleException;
import com.padelbooking.api.exception.ResourceNotFoundException;
import com.padelbooking.api.exception.RichiestaNonValidaException;
import com.padelbooking.api.model.Avviso;
import com.padelbooking.api.model.Campo;
import com.padelbooking.api.model.Partecipazione;
import com.padelbooking.api.model.Partita;
import com.padelbooking.api.model.StatoPartita;
import com.padelbooking.api.model.TipoPartecipazione;
import com.padelbooking.api.model.Utente;
import com.padelbooking.api.repository.AvvisoRepository;
import com.padelbooking.api.repository.CampoRepository;
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

// Area amministratore (UC_GA_1–6, UC_GU_3): le regole dei giocatori non valgono, in particolare i limiti
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

    // Una partita iniziata non ha più un avviso aperto. L'inizio effettivo cade al più il giorno dopo
    // data_partita, quindi una partita non ancora iniziata ha data_partita da ieri in poi
    private static final int GIORNI_INDIETRO_AVVISI = 1;

    // Vincolo unico sugli slot attivi: la colonna generata slot_attivo vale NULL solo per le annullate
    private static final String VINCOLO_SLOT_UNICO = "uk_partita_slot";

    private static final String NOME_GIOCATORE_CAMPO = "Giocatore del campo";

    // Messaggi (RNF_U_3): fanno parte del contratto con il client
    private static final String MESSAGGIO_LIVELLO_NON_VALIDO = "Il livello deve essere tra 0 e 5 con passo 0,5";
    private static final String MESSAGGIO_UTENTE_NON_TROVATO = "Utente non trovato";
    private static final String MESSAGGIO_ALMENO_UN_UTENTE = "Almeno un giocatore deve essere un utente";
    private static final String MESSAGGIO_MASSIMO_GIOCATORI = "Una partita può avere al massimo 4 giocatori";
    private static final String MESSAGGIO_TELEFONI_DUPLICATI = "L'elenco dei partecipanti contiene telefoni duplicati";
    private static final String MESSAGGIO_PARTECIPANTI_NON_MODIFICABILI =
            "Si possono modificare solo le partite non annullate e non giocate";
    private static final String MESSAGGIO_CREATORE_NON_RIMOVIBILE =
            "Il creatore non può essere rimosso: annulla la partita";
    private static final String MESSAGGIO_ADMIN_NON_PARTECIPA = "Un amministratore non può partecipare";
    private static final String MESSAGGIO_RISULTATO_NON_MODIFICABILE =
            "Il risultato si può modificare solo per le partite confermate o giocate";
    private static final String MESSAGGIO_STATO_NON_VALIDO = "Stato non valido";
    private static final String MESSAGGIO_SLOT_OCCUPATO = "Lo slot è già occupato da un'altra partita";
    private static final String MESSAGGIO_GIA_ANNULLATA = "La partita è già annullata";

    private final PartitaRepository partitaRepository;
    private final CampoRepository campoRepository;
    private final AvvisoRepository avvisoRepository;
    private final UtenteRepository utenteRepository;
    private final PartitaService partitaService;
    private final PunteggiService punteggiService;
    private final EntityManager entityManager;
    private final Clock clock;

    public AdminService(PartitaRepository partitaRepository,
                        CampoRepository campoRepository,
                        AvvisoRepository avvisoRepository,
                        UtenteRepository utenteRepository,
                        PartitaService partitaService,
                        PunteggiService punteggiService,
                        EntityManager entityManager,
                        Clock clock) {
        this.partitaRepository = partitaRepository;
        this.campoRepository = campoRepository;
        this.avvisoRepository = avvisoRepository;
        this.utenteRepository = utenteRepository;
        this.partitaService = partitaService;
        this.punteggiService = punteggiService;
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
    @Transactional(readOnly = true)
    public List<AdminDTO.AvvisoResponse> getAvvisi() {
        LocalDateTime adesso = LocalDateTime.now(clock);
        return avvisoRepository.findAperti(StatoPartita.ANNULLATA, adesso.toLocalDate().minusDays(GIORNI_INDIETRO_AVVISI))
                .stream()
                .filter(a -> !RegoleTempoPartita.iniziata(a.getPartita(), adesso))
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
    @Transactional
    public PartitaDTO.DettaglioResponse modificaPartecipanti(Integer idPartita, List<String> telefoni) {
        if (telefoni == null || telefoni.isEmpty()) {
            throw new RichiestaNonValidaException(MESSAGGIO_ALMENO_UN_UTENTE);
        }
        Set<String> telefoniFinali = new LinkedHashSet<>(telefoni);
        if (telefoniFinali.size() != telefoni.size()) {
            throw new RichiestaNonValidaException(MESSAGGIO_TELEFONI_DUPLICATI);
        }

        Partita partita = trovaConLockOLancia(idPartita);
        StatoPartita stato = partita.getStato();
        if (stato == StatoPartita.ANNULLATA || stato == StatoPartita.GIOCATA) {
            throw new BusinessRuleException(MESSAGGIO_PARTECIPANTI_NON_MODIFICABILI);
        }
        long postiCampo = partita.getPartecipazioni().stream()
                .filter(pa -> pa.getTipo() == TipoPartecipazione.CAMPO)
                .count();
        if (telefoniFinali.size() + postiCampo > POSTI_MAX) {
            throw new RichiestaNonValidaException(MESSAGGIO_MASSIMO_GIOCATORI);
        }
        // telefono_creatore è un riferimento obbligatorio: il creatore resta finché resta la partita
        if (!telefoniFinali.contains(partita.getCreatore().getTelefono())) {
            throw new BusinessRuleException(MESSAGGIO_CREATORE_NON_RIMOVIBILE);
        }

        List<Partecipazione> postiUtente = partita.getPartecipazioni().stream()
                .filter(pa -> pa.getTipo() == TipoPartecipazione.UTENTE)
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

        // postiUtente è una copia: si può togliere dalla lista della partita mentre la si scorre
        for (Partecipazione posto : postiUtente) {
            if (!telefoniFinali.contains(posto.getUtente().getTelefono())) {
                partita.rimuoviPartecipazione(posto);
            }
        }
        for (Utente utente : nuovi) {
            // In una partita già confermata nessuno deve più confermare
            partita.aggiungiPartecipazione(new Partecipazione(TipoPartecipazione.UTENTE, utente, false,
                    stato == StatoPartita.CONFERMATA));
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
