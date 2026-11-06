package com.padelbooking.api.service;

import com.padelbooking.api.dto.PrenotazioneDTO;
import com.padelbooking.api.exception.BusinessRuleException;
import com.padelbooking.api.exception.ResourceNotFoundException;
import com.padelbooking.api.exception.RichiestaNonValidaException;
import com.padelbooking.api.model.Campo;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class PrenotazioneService {

    // Durata fissa di ogni prenotazione, coerente con la regola di business del progetto
    private static final int DURATA_SLOT_MINUTI = 90;

    // Il limite di 24 ore per l'annullamento è in RegoleTempoPartita, condiviso con PartitaService

    // Nome del vincolo unico che impedisce due partite attive sullo stesso slot
    private static final String VINCOLO_SLOT_UNICO = "uk_partita_slot";

    private static final String MESSAGGIO_SLOT_OCCUPATO = "Lo slot selezionato è già stato prenotato";

    // R9, D6: il limite in giorni sta in RegoleTempoPartita, condiviso con le partite create dall'admin
    private static final String MESSAGGIO_OLTRE_LIMITE_PRENOTAZIONE =
            "Si può prenotare al massimo " + RegoleTempoPartita.getGiorniPrenotazioneMax() + " giorni in anticipo";

    // R11: con il creatore restano al massimo 3 posti da assegnare agli invitati
    private static final int INVITATI_MAX = RegoleConfermaPartita.getPostiMax() - 1;

    // Messaggi degli invitati alla prenotazione (RNF_U_3): 404 e 409 sono gli stessi di POST /api/partite/{id}/inviti
    private static final String MESSAGGIO_TROPPI_INVITATI = "Si possono invitare al massimo " + INVITATI_MAX + " utenti";
    private static final String MESSAGGIO_INVITATI_DUPLICATI = "L'elenco degli invitati contiene telefoni duplicati";
    private static final String MESSAGGIO_INVITATO_MANCANTE = "Il telefono dell'invitato è obbligatorio";
    private static final String MESSAGGIO_INVITA_SE_STESSO = "Non puoi invitare te stesso";
    private static final String MESSAGGIO_UTENTE_NON_TROVATO = "Utente non trovato";
    private static final String MESSAGGIO_NON_INVITA_ADMIN = "Non è possibile invitare un amministratore";

    private final PartitaRepository partitaRepository;
    private final UtenteRepository utenteRepository;
    private final CampoService campoService;
    private final Clock clock;

    public PrenotazioneService(PartitaRepository partitaRepository,
                                UtenteRepository utenteRepository,
                                CampoService campoService,
                                Clock clock) {
        this.partitaRepository = partitaRepository;
        this.utenteRepository = utenteRepository;
        this.campoService = campoService;
        this.clock = clock;
    }

    // ============================================
    // Calcolo slot disponibili per un campo in una data
    // ============================================
    public List<PrenotazioneDTO.SlotResponse> getSlotDisponibili(Integer idCampo, LocalDate data) {
        Campo campo = campoService.trovaCampoOLancia(idCampo);

        List<Partita> prenotazioniEsistenti = trovaPartiteAttive(idCampo, data);

        // Intervalli occupati espressi come [inizio, fine) in minuti trascorsi dall'apertura,
        // così da poter confrontare le sovrapposizioni indipendentemente dal fatto che la
        // prenotazione sia o meno allineata alla griglia degli slot generati sotto.
        List<long[]> intervalliOccupati = prenotazioniEsistenti.stream()
                .map(p -> {
                    long inizio = minutiDaApertura(campo, p.getOraInizio());
                    return new long[] { inizio, inizio + DURATA_SLOT_MINUTI };
                })
                .toList();

        List<PrenotazioneDTO.SlotResponse> slots = new ArrayList<>();

        // Lavoriamo per "minuti trascorsi dall'apertura" invece che per LocalTime assoluti:
        // in questo modo il calcolo funziona anche quando il campo chiude dopo mezzanotte
        // (es. apertura 18:00, chiusura 02:00), senza che il wrap-around delle 00:00 rompa il ciclo.
        long minutiTotaliApertura = minutiDiAperturaTotali(campo);
        long offset = 0;

        while (offset + DURATA_SLOT_MINUTI <= minutiTotaliApertura) {
            LocalTime inizioSlot = campo.getOraApertura().plusMinutes(offset);
            LocalTime fineSlot = campo.getOraApertura().plusMinutes(offset + DURATA_SLOT_MINUTI);

            long slotInizio = offset;
            long slotFine = offset + DURATA_SLOT_MINUTI;
            boolean disponibile = intervalliOccupati.stream()
                    .noneMatch(i -> siSovrappongono(i[0], i[1], slotInizio, slotFine));

            slots.add(new PrenotazioneDTO.SlotResponse(idCampo, campo.getNome(), inizioSlot, fineSlot, disponibile));

            offset += DURATA_SLOT_MINUTI;
        }

        return slots;
    }

    // Due intervalli [aInizio, aFine) e [bInizio, bFine) si sovrappongono se ciascuno
    // inizia prima che l'altro finisca.
    private boolean siSovrappongono(long aInizio, long aFine, long bInizio, long bFine) {
        return aInizio < bFine && bInizio < aFine;
    }

    // ============================================
    // Creazione prenotazione
    // ============================================
    // Il creatore è l'utente autenticato (telefono dal JWT). R11: può indicare subito fino a 3 invitati, che
    // entrano come con POST /api/partite/{id}/inviti; gli altri posti si riempiono dopo con inviti o bacheca.
    // Una sola transazione: se un invitato non è valido non resta salvata neanche la partita.
    @Transactional
    public PrenotazioneDTO.Response create(PrenotazioneDTO.CreateRequest request, String telefonoCreatore) {
        List<String> telefoniInvitati = request.getInvitati() == null ? List.of() : request.getInvitati();
        validaElencoInvitati(telefoniInvitati, telefonoCreatore);

        Partita partita = nuovaPartitaSuSlot(request.getIdCampo(), request.getDataPrenotazione(), request.getOraInizio());

        Utente creatore = trovaUtenteOLancia(telefonoCreatore);
        List<Utente> invitati = new ArrayList<>();
        for (String telefono : telefoniInvitati) {
            invitati.add(trovaInvitabileOLancia(telefono));
        }
        // Come in AdminService.creaPartita: gli INSERT prendono il lock sulla riga utente in ordine di telefono,
        // per non andare in deadlock con un risultato registrato in parallelo (PunteggiService)
        invitati.sort(Comparator.comparing(Utente::getTelefono));

        partita.setCreatore(creatore);
        // Il valore è già validato dal @Pattern del DTO
        partita.setTipo(TipoPartita.valueOf(request.getTipo()));

        // D2: una prenotazione a meno di 12 ore dall'inizio non ha più la finestra di conferma,
        // quindi creatore e invitati nascono confermati
        boolean confermata = RegoleConfermaPartita.confermataAllIngresso(partita, LocalDateTime.now(clock));

        // Il creatore entra direttamente nella partita: non è un invito da accettare, per questo invitato = false.
        // Va aggiunto per primo, perché la risposta mette in giocatore 1 il creatore e in 2-4 gli altri in ordine di id
        partita.aggiungiPartecipazione(new Partecipazione(TipoPartecipazione.UTENTE, creatore, false, confermata));
        for (Utente invitato : invitati) {
            partita.aggiungiPartecipazione(new Partecipazione(TipoPartecipazione.UTENTE, invitato, true, confermata));
        }

        // Con 4 utenti tutti confermati, cioè solo per una prenotazione tardiva, la partita nasce CONFERMATA
        if (RegoleConfermaPartita.confermaSeCompleta(partita)) {
            // T13: notifica ADMIN_CONFERMA all'amministratore (RF_GN_8)
        }

        // T13: notifiche di invito (RF_GN_1)

        return toResponse(salvaNuovaPartita(partita));
    }

    // Controlla che lo slot sia prenotabile e prepara la partita CREATA, ancora da salvare: creatore, tipo e
    // posti li aggiunge il chiamante. Le regole di slot sono le stesse per l'utente e per l'admin (T12).
    public Partita nuovaPartitaSuSlot(Integer idCampo, LocalDate data, LocalTime oraInizio) {
        Campo campo = campoService.trovaCampoOLancia(idCampo);

        LocalTime oraFine = oraInizio.plusMinutes(DURATA_SLOT_MINUTI);

        validaSlotAllInternoOrarioApertura(campo, oraInizio);
        validaDataNonPassata(campo, data, oraInizio);
        validaEntroLimitePrenotazione(data);

        // Controlliamo la sovrapposizione con qualsiasi partita attiva sullo stesso
        // campo/data, non solo l'uguaglianza esatta dell'ora di inizio: altrimenti due
        // prenotazioni che si accavallano parzialmente (es. 18:00-19:30 e 18:30-20:00)
        // potrebbero coesistere.
        long nuovoInizioOffset = minutiDaApertura(campo, oraInizio);
        long nuovoFineOffset = nuovoInizioOffset + DURATA_SLOT_MINUTI;

        boolean slotOccupato = trovaPartiteAttive(idCampo, data)
                .stream()
                .anyMatch(p -> {
                    long inizioEsistente = minutiDaApertura(campo, p.getOraInizio());
                    long fineEsistente = inizioEsistente + DURATA_SLOT_MINUTI;
                    return siSovrappongono(inizioEsistente, fineEsistente, nuovoInizioOffset, nuovoFineOffset);
                });

        if (slotOccupato) {
            throw new BusinessRuleException(MESSAGGIO_SLOT_OCCUPATO);
        }

        Partita partita = new Partita();
        partita.setCampo(campo);
        partita.setDataPartita(data);
        partita.setOraInizio(oraInizio);
        partita.setOraFine(oraFine);
        partita.setStato(StatoPartita.CREATA);
        return partita;
    }

    // Con la generazione IDENTITY il save esegue subito gli INSERT, anche dentro una transazione: la violazione
    // arriva qui e non al commit
    public Partita salvaNuovaPartita(Partita partita) {
        try {
            return partitaRepository.save(partita);
        } catch (DataIntegrityViolationException ex) {
            // Il controllo di sovrapposizione non basta con richieste simultanee:
            // in quel caso è il vincolo unico sul DB a rifiutare la seconda partita.
            if (violaVincolo(ex, VINCOLO_SLOT_UNICO)) {
                throw new BusinessRuleException(MESSAGGIO_SLOT_OCCUPATO);
            }
            throw ex;
        }
    }

    // ============================================
    // Cancellazione prenotazione
    // ============================================
    @Transactional
    public void cancel(Integer idPrenotazione, String telefonoUtenteRichiedente, boolean isAdmin) {
        Partita partita = partitaRepository.findById(idPrenotazione)
                .orElseThrow(() -> new ResourceNotFoundException("Prenotazione non trovata con id " + idPrenotazione));

        boolean isProprietario = partita.getCreatore().getTelefono().equals(telefonoUtenteRichiedente);
        if (!isProprietario && !isAdmin) {
            throw new BusinessRuleException("Non hai i permessi per cancellare questa prenotazione");
        }

        if (partita.getStato() == StatoPartita.ANNULLATA) {
            throw new BusinessRuleException("La prenotazione è già stata annullata");
        }

        // Una partita giocata ha (o avrà) un risultato e dei punti assegnati: non si annulla, neanche da admin
        if (partita.getStato() == StatoPartita.GIOCATA) {
            throw new BusinessRuleException("Non è possibile annullare una partita già giocata");
        }

        // Istante reale, non data_partita + ora: una partita delle 00:30 della serata di sabato
        // inizia domenica, e le 24 ore vanno contate fino a lì (lo fa RegoleTempoPartita)
        if (!isAdmin && !RegoleTempoPartita.primaDelLimiteModifiche(partita, LocalDateTime.now(clock))) {
            throw new BusinessRuleException("Non è possibile cancellare una prenotazione a meno di "
                    + RegoleTempoPartita.getLimiteModificheOre() + " ore dall'inizio");
        }

        // La riga resta in tabella: con stato ANNULLATA la colonna generata slot_attivo diventa NULL,
        // quindi il vincolo unico la ignora e lo slot torna prenotabile
        partita.setStato(StatoPartita.ANNULLATA);
    }

    // ============================================
    // Prenotazioni di un utente (storico)
    // ============================================
    @Transactional(readOnly = true)
    public List<PrenotazioneDTO.Response> getByUtente(String telefono) {
        if (!utenteRepository.existsByTelefono(telefono)) {
            throw new ResourceNotFoundException("Utente non trovato con telefono " + telefono);
        }
        // Ordiniamo in Java sull'istante reale di inizio: l'ORDER BY della query (data_partita, ora_inizio)
        // metterebbe la partita delle 00:30 di una serata prima di quella delle 23:00 della stessa serata
        return partitaRepository
                .findByPartecipanteTelefono(telefono, TipoPartecipazione.UTENTE, StatoPartita.ANNULLATA)
                .stream()
                .sorted(Comparator.comparing((Partita p) -> inizioEffettivo(p)).reversed())
                .map(this::toResponse)
                .toList();
    }

    // ============================================
    // Prossima prenotazione di un utente, dato il telefono
    // ============================================
    @Transactional(readOnly = true)
    public PrenotazioneDTO.Response getProssimaByTelefono(String telefono) {
        if (!utenteRepository.existsByTelefono(telefono)) {
            throw new ResourceNotFoundException("Utente non trovato con telefono " + telefono);
        }

        LocalDateTime adesso = LocalDateTime.now(clock);

        // data_partita è la data della serata: una partita dopo mezzanotte ha data_partita = ieri
        // ma inizia oggi. Per questo carichiamo le candidate da ieri in poi (dalle 00:00) e scegliamo
        // in Java la prima con inizio effettivo non ancora passato.
        List<Partita> candidate = partitaRepository.findProssimeByPartecipanteTelefono(
                telefono, TipoPartecipazione.UTENTE, StatoPartita.ANNULLATA,
                adesso.toLocalDate().minusDays(1), LocalTime.MIN);

        Partita prossima = candidate.stream()
                .filter(p -> !inizioEffettivo(p).isBefore(adesso))
                .min(Comparator.comparing(this::inizioEffettivo))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Nessuna prenotazione futura trovata per il telefono " + telefono));

        return toResponse(prossima);
    }

    // ============================================
    // Helper privati
    // ============================================
    private void validaSlotAllInternoOrarioApertura(Campo campo, LocalTime oraInizio) {
        long minutiTotaliApertura = minutiDiAperturaTotali(campo);
        long offsetInizio = minutiDaApertura(campo, oraInizio);

        // Lo slot è valido solo se, partendo dall'apertura, sia l'inizio che la fine
        // (inizio + durata) cadono entro il totale di minuti in cui il campo è aperto.
        // Usare gli offset in minuti anziché confrontare direttamente i LocalTime evita
        // il problema del wrap-around a mezzanotte per i campi che chiudono dopo le 00:00.
        boolean fuoriOrario = offsetInizio + DURATA_SLOT_MINUTI > minutiTotaliApertura;

        if (fuoriOrario) {
            throw new BusinessRuleException("L'orario richiesto è fuori dalla fascia di apertura del campo");
        }
    }

    // ============================================
    // Helper per la gestione degli orari con chiusura oltre mezzanotte
    // ============================================

    // Durata totale (in minuti) della fascia di apertura del campo, calcolata a partire
    // dall'ora di apertura. Se la chiusura è "prima" dell'apertura in termini di orologio
    // (es. apertura 18:00, chiusura 02:00), significa che il campo chiude il giorno dopo:
    // in tal caso si somma il tratto fino a mezzanotte con quello dopo mezzanotte.
    // Se apertura e chiusura coincidono, il campo è considerato aperto 24 ore su 24.
    private long minutiDiAperturaTotali(Campo campo) {
        long minutiApertura = campo.getOraApertura().toSecondOfDay() / 60L;
        long minutiChiusura = campo.getOraChiusura().toSecondOfDay() / 60L;

        if (minutiChiusura == minutiApertura) {
            return 24 * 60L;
        }
        if (minutiChiusura > minutiApertura) {
            return minutiChiusura - minutiApertura;
        }
        return (24 * 60L - minutiApertura) + minutiChiusura;
    }

    // Minuti trascorsi dall'apertura del campo fino all'orario indicato, gestendo
    // correttamente il caso in cui tale orario sia "dopo mezzanotte" (quindi numericamente
    // minore dell'ora di apertura, ma comunque successivo cronologicamente).
    private long minutiDaApertura(Campo campo, LocalTime orario) {
        long minutiApertura = campo.getOraApertura().toSecondOfDay() / 60L;
        long minutiOrario = orario.toSecondOfDay() / 60L;

        if (minutiOrario >= minutiApertura) {
            return minutiOrario - minutiApertura;
        }
        return (24 * 60L - minutiApertura) + minutiOrario;
    }

    private void validaDataNonPassata(Campo campo, LocalDate data, LocalTime oraInizio) {
        LocalDateTime inizio = inizioEffettivo(campo, data, oraInizio);
        if (inizio.isBefore(LocalDateTime.now(clock))) {
            throw new BusinessRuleException("Non è possibile prenotare uno slot nel passato");
        }
    }

    private void validaEntroLimitePrenotazione(LocalDate data) {
        if (RegoleTempoPartita.oltreLimitePrenotazione(data, LocalDate.now(clock))) {
            throw new BusinessRuleException(MESSAGGIO_OLTRE_LIMITE_PRENOTAZIONE);
        }
    }

    // R11: controlli sull'elenco che non richiedono il database, prima di toccare lo slot.
    // Chi esiste e chi è admin lo controlla trovaInvitabileOLancia.
    private void validaElencoInvitati(List<String> telefoni, String telefonoCreatore) {
        if (telefoni.size() > INVITATI_MAX) {
            throw new RichiestaNonValidaException(MESSAGGIO_TROPPI_INVITATI);
        }
        Set<String> visti = new HashSet<>();
        for (String telefono : telefoni) {
            if (telefono == null || telefono.isBlank()) {
                throw new RichiestaNonValidaException(MESSAGGIO_INVITATO_MANCANTE);
            }
            if (telefono.equals(telefonoCreatore)) {
                throw new RichiestaNonValidaException(MESSAGGIO_INVITA_SE_STESSO);
            }
            if (!visti.add(telefono)) {
                throw new RichiestaNonValidaException(MESSAGGIO_INVITATI_DUPLICATI);
            }
        }
    }

    private Utente trovaInvitabileOLancia(String telefono) {
        Utente utente = utenteRepository.findById(telefono)
                .orElseThrow(() -> new ResourceNotFoundException(MESSAGGIO_UTENTE_NON_TROVATO));
        if (Boolean.TRUE.equals(utente.getIsAdmin())) {
            throw new BusinessRuleException(MESSAGGIO_NON_INVITA_ADMIN);
        }
        return utente;
    }

    // L'inizio effettivo (data della serata + eventuale giorno dopo mezzanotte) è calcolato
    // in RegoleTempoPartita, condiviso con PartitaService
    private LocalDateTime inizioEffettivo(Campo campo, LocalDate dataSerata, LocalTime oraInizio) {
        return RegoleTempoPartita.inizioEffettivo(campo, dataSerata, oraInizio);
    }

    private LocalDateTime inizioEffettivo(Partita partita) {
        return RegoleTempoPartita.inizioEffettivo(partita);
    }

    private Utente trovaUtenteOLancia(String telefono) {
        return utenteRepository.findById(telefono)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato con telefono " + telefono));
    }

    // Le partite annullate restano in tabella ma non occupano lo slot
    private List<Partita> trovaPartiteAttive(Integer idCampo, LocalDate data) {
        return partitaRepository.findByCampoIdAndDataPartitaAndStatoNot(idCampo, data, StatoPartita.ANNULLATA);
    }

    // Cerca il nome del vincolo nella catena delle cause: il messaggio di MySQL lo riporta
    // (es. "Duplicate entry ... for key 'partita.uk_partita_slot'")
    private boolean violaVincolo(Throwable ex, String nomeVincolo) {
        for (Throwable causa = ex; causa != null; causa = causa.getCause()) {
            if (causa.getMessage() != null && causa.getMessage().contains(nomeVincolo)) {
                return true;
            }
        }
        return false;
    }

    // Il formato della risposta è quello della vecchia tabella prenotazione, che il client usa già:
    // giocatore 1 = creatore, giocatori 2-4 = le altre partecipazioni UTENTE in ordine di id
    // (garantito da @OrderBy su Partita.partecipazioni, e dall'ordine di inserimento appena dopo la create).
    private PrenotazioneDTO.Response toResponse(Partita p) {
        String telefonoCreatore = p.getCreatore().getTelefono();
        List<String> altriGiocatori = p.getPartecipazioni().stream()
                .filter(pa -> pa.getTipo() == TipoPartecipazione.UTENTE)
                .map(pa -> pa.getUtente().getTelefono())
                .filter(telefono -> !telefono.equals(telefonoCreatore))
                .toList();

        return new PrenotazioneDTO.Response(
                p.getId(),
                p.getCampo().getId(),
                p.getCampo().getNome(),
                telefonoCreatore,
                altriGiocatori.size() > 0 ? altriGiocatori.get(0) : null,
                altriGiocatori.size() > 1 ? altriGiocatori.get(1) : null,
                altriGiocatori.size() > 2 ? altriGiocatori.get(2) : null,
                p.getDataPartita(),
                p.getOraInizio(),
                p.getOraFine(),
                p.getStato().name().toLowerCase(Locale.ROOT),
                p.getTipo().name()
        );
    }
}
