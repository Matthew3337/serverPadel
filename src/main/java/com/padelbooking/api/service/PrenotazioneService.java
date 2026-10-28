package com.padelbooking.api.service;

import com.padelbooking.api.dto.PrenotazioneDTO;
import com.padelbooking.api.exception.BusinessRuleException;
import com.padelbooking.api.exception.ResourceNotFoundException;
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

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class PrenotazioneService {

    // Durata fissa di ogni prenotazione, coerente con la regola di business del progetto
    private static final int DURATA_SLOT_MINUTI = 90;

    // Quanto tempo prima si può ancora cancellare una prenotazione
    private static final int LIMITE_CANCELLAZIONE_ORE = 24;

    // Tipo usato quando il client non lo indica (i client esistenti non inviano il campo)
    private static final TipoPartita TIPO_PARTITA_PREDEFINITO = TipoPartita.PRIVATA;

    // Nome del vincolo unico che impedisce due partite attive sullo stesso slot
    private static final String VINCOLO_SLOT_UNICO = "uk_partita_slot";

    private static final String MESSAGGIO_SLOT_OCCUPATO = "Lo slot selezionato è già stato prenotato";

    private final PartitaRepository partitaRepository;
    private final UtenteRepository utenteRepository;
    private final CampoService campoService;

    public PrenotazioneService(PartitaRepository partitaRepository,
                                UtenteRepository utenteRepository,
                                CampoService campoService) {
        this.partitaRepository = partitaRepository;
        this.utenteRepository = utenteRepository;
        this.campoService = campoService;
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
    public PrenotazioneDTO.Response create(PrenotazioneDTO.CreateRequest request) {
        Campo campo = campoService.trovaCampoOLancia(request.getIdCampo());

        LocalTime oraInizio = request.getOraInizio();
        LocalTime oraFine = oraInizio.plusMinutes(DURATA_SLOT_MINUTI);

        validaSlotAllInternoOrarioApertura(campo, oraInizio);
        validaDataNonPassata(request.getDataPrenotazione(), oraInizio);

        // Controlliamo la sovrapposizione con qualsiasi partita attiva sullo stesso
        // campo/data, non solo l'uguaglianza esatta dell'ora di inizio: altrimenti due
        // prenotazioni che si accavallano parzialmente (es. 18:00-19:30 e 18:30-20:00)
        // potrebbero coesistere.
        long nuovoInizioOffset = minutiDaApertura(campo, oraInizio);
        long nuovoFineOffset = nuovoInizioOffset + DURATA_SLOT_MINUTI;

        boolean slotOccupato = trovaPartiteAttive(request.getIdCampo(), request.getDataPrenotazione())
                .stream()
                .anyMatch(p -> {
                    long inizioEsistente = minutiDaApertura(campo, p.getOraInizio());
                    long fineEsistente = inizioEsistente + DURATA_SLOT_MINUTI;
                    return siSovrappongono(inizioEsistente, fineEsistente, nuovoInizioOffset, nuovoFineOffset);
                });

        if (slotOccupato) {
            throw new BusinessRuleException(MESSAGGIO_SLOT_OCCUPATO);
        }

        List<Utente> giocatori = trovaGiocatori(request);

        Partita partita = new Partita();
        partita.setCampo(campo);
        partita.setCreatore(giocatori.get(0));
        partita.setDataPartita(request.getDataPrenotazione());
        partita.setOraInizio(oraInizio);
        partita.setOraFine(oraFine);
        partita.setTipo(request.getTipo() != null ? TipoPartita.valueOf(request.getTipo()) : TIPO_PARTITA_PREDEFINITO);
        partita.setStato(StatoPartita.CREATA);

        // I giocatori indicati nella richiesta entrano direttamente nella partita:
        // non sono inviti da accettare, per questo invitato = false
        for (Utente giocatore : giocatori) {
            partita.aggiungiPartecipazione(new Partecipazione(TipoPartecipazione.UTENTE, giocatore, false, false));
        }

        Partita salvata;
        try {
            salvata = partitaRepository.save(partita);
        } catch (DataIntegrityViolationException ex) {
            // Il controllo di sovrapposizione sopra non basta con richieste simultanee:
            // in quel caso è il vincolo unico sul DB a rifiutare la seconda partita.
            if (violaVincolo(ex, VINCOLO_SLOT_UNICO)) {
                throw new BusinessRuleException(MESSAGGIO_SLOT_OCCUPATO);
            }
            throw ex;
        }
        return toResponse(salvata);
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

        LocalDateTime inizioPrenotazione = LocalDateTime.of(partita.getDataPartita(), partita.getOraInizio());
        long oreRimanenti = Duration.between(LocalDateTime.now(), inizioPrenotazione).toHours();

        if (!isAdmin && oreRimanenti < LIMITE_CANCELLAZIONE_ORE) {
            throw new BusinessRuleException(
                    "Non è possibile cancellare una prenotazione a meno di " + LIMITE_CANCELLAZIONE_ORE + " ore dall'inizio");
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
        return partitaRepository
                .findByPartecipanteTelefono(telefono, TipoPartecipazione.UTENTE, StatoPartita.ANNULLATA)
                .stream()
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

        LocalDate oggi = LocalDate.now();
        LocalTime oraAttuale = LocalTime.now();

        List<Partita> prossime = partitaRepository.findProssimeByPartecipanteTelefono(
                telefono, TipoPartecipazione.UTENTE, StatoPartita.ANNULLATA, oggi, oraAttuale);

        if (prossime.isEmpty()) {
            throw new ResourceNotFoundException("Nessuna prenotazione futura trovata per il telefono " + telefono);
        }

        return toResponse(prossime.get(0));
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

    private void validaDataNonPassata(LocalDate data, LocalTime oraInizio) {
        LocalDateTime inizio = LocalDateTime.of(data, oraInizio);
        if (inizio.isBefore(LocalDateTime.now())) {
            throw new BusinessRuleException("Non è possibile prenotare uno slot nel passato");
        }
    }

    private Utente trovaUtenteOLancia(String telefono) {
        return utenteRepository.findById(telefono)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato con telefono " + telefono));
    }

    // Le partite annullate restano in tabella ma non occupano lo slot
    private List<Partita> trovaPartiteAttive(Integer idCampo, LocalDate data) {
        return partitaRepository.findByCampoIdAndDataPartitaAndStatoNot(idCampo, data, StatoPartita.ANNULLATA);
    }

    // Giocatore 1 per primo (diventa il creatore), poi i giocatori 2-4 indicati, nell'ordine della richiesta
    private List<Utente> trovaGiocatori(PrenotazioneDTO.CreateRequest request) {
        List<String> telefoni = new ArrayList<>();
        telefoni.add(request.getTelefonoGiocatore1());
        telefoni.add(request.getTelefonoGiocatore2());
        telefoni.add(request.getTelefonoGiocatore3());
        telefoni.add(request.getTelefonoGiocatore4());

        // Ogni utente può avere una sola partecipazione per partita (vincolo uk_partecipazione_utente)
        Set<String> giaIndicati = new HashSet<>();
        List<Utente> giocatori = new ArrayList<>();
        for (String telefono : telefoni.stream().filter(Objects::nonNull).toList()) {
            if (!giaIndicati.add(telefono)) {
                throw new BusinessRuleException("Lo stesso giocatore non può essere indicato più di una volta");
            }
            giocatori.add(trovaUtenteOLancia(telefono));
        }
        return giocatori;
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
                p.getStato().name().toLowerCase(Locale.ROOT)
        );
    }
}
