package com.padelbooking.api.service;

import com.padelbooking.api.dto.PrenotazioneDTO;
import com.padelbooking.api.exception.BusinessRuleException;
import com.padelbooking.api.exception.ResourceNotFoundException;
import com.padelbooking.api.model.Campo;
import com.padelbooking.api.model.Prenotazione;
import com.padelbooking.api.model.Utente;
import com.padelbooking.api.repository.PrenotazioneRepository;
import com.padelbooking.api.repository.UtenteRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PrenotazioneService {

    // Durata fissa di ogni prenotazione, coerente con la regola di business del progetto
    private static final int DURATA_SLOT_MINUTI = 90;

    // Quanto tempo prima si può ancora cancellare una prenotazione
    private static final int LIMITE_CANCELLAZIONE_ORE = 24;

    private final PrenotazioneRepository prenotazioneRepository;
    private final UtenteRepository utenteRepository;
    private final CampoService campoService;

    public PrenotazioneService(PrenotazioneRepository prenotazioneRepository,
                                UtenteRepository utenteRepository,
                                CampoService campoService) {
        this.prenotazioneRepository = prenotazioneRepository;
        this.utenteRepository = utenteRepository;
        this.campoService = campoService;
    }

    // ============================================
    // Calcolo slot disponibili per un campo in una data
    // ============================================
    public List<PrenotazioneDTO.SlotResponse> getSlotDisponibili(Integer idCampo, LocalDate data) {
        Campo campo = campoService.trovaCampoOLancia(idCampo);

        List<Prenotazione> prenotazioniEsistenti = prenotazioneRepository
                .findByCampoIdAndDataPrenotazione(idCampo, data);

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

            slots.add(new PrenotazioneDTO.SlotResponse(inizioSlot, fineSlot, disponibile));

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

        // Controlliamo la sovrapposizione con qualsiasi prenotazione esistente sullo stesso
        // campo/data, non solo l'uguaglianza esatta dell'ora di inizio: altrimenti due
        // prenotazioni che si accavallano parzialmente (es. 18:00-19:30 e 18:30-20:00)
        // potrebbero coesistere.
        long nuovoInizioOffset = minutiDaApertura(campo, oraInizio);
        long nuovoFineOffset = nuovoInizioOffset + DURATA_SLOT_MINUTI;

        boolean slotOccupato = prenotazioneRepository
                .findByCampoIdAndDataPrenotazione(request.getIdCampo(), request.getDataPrenotazione())
                .stream()
                .anyMatch(p -> {
                    long inizioEsistente = minutiDaApertura(campo, p.getOraInizio());
                    long fineEsistente = inizioEsistente + DURATA_SLOT_MINUTI;
                    return siSovrappongono(inizioEsistente, fineEsistente, nuovoInizioOffset, nuovoFineOffset);
                });

        if (slotOccupato) {
            throw new BusinessRuleException("Lo slot selezionato è già stato prenotato");
        }

        Prenotazione prenotazione = new Prenotazione();
        prenotazione.setCampo(campo);
        prenotazione.setGiocatore1(trovaUtenteOLancia(request.getTelefonoGiocatore1()));
        prenotazione.setGiocatore2(trovaUtenteSeValorizzato(request.getTelefonoGiocatore2()));
        prenotazione.setGiocatore3(trovaUtenteSeValorizzato(request.getTelefonoGiocatore3()));
        prenotazione.setGiocatore4(trovaUtenteSeValorizzato(request.getTelefonoGiocatore4()));
        prenotazione.setDataPrenotazione(request.getDataPrenotazione());
        prenotazione.setOraInizio(oraInizio);
        prenotazione.setOraFine(oraFine);

        Prenotazione salvata = prenotazioneRepository.save(prenotazione);
        return toResponse(salvata);
    }

    // ============================================
    // Cancellazione prenotazione
    // ============================================
    public void cancel(Integer idPrenotazione, String telefonoUtenteRichiedente, boolean isAdmin) {
        Prenotazione prenotazione = prenotazioneRepository.findById(idPrenotazione)
                .orElseThrow(() -> new ResourceNotFoundException("Prenotazione non trovata con id " + idPrenotazione));

        boolean isProprietario = prenotazione.getGiocatore1().getTelefono().equals(telefonoUtenteRichiedente);
        if (!isProprietario && !isAdmin) {
            throw new BusinessRuleException("Non hai i permessi per cancellare questa prenotazione");
        }

        LocalDateTime inizioPrenotazione = LocalDateTime.of(prenotazione.getDataPrenotazione(), prenotazione.getOraInizio());
        long oreRimanenti = Duration.between(LocalDateTime.now(), inizioPrenotazione).toHours();

        if (!isAdmin && oreRimanenti < LIMITE_CANCELLAZIONE_ORE) {
            throw new BusinessRuleException(
                    "Non è possibile cancellare una prenotazione a meno di " + LIMITE_CANCELLAZIONE_ORE + " ore dall'inizio");
        }

        // Non esiste una colonna "stato" nello schema: cancellare significa
        // rimuovere fisicamente la riga dalla tabella prenotazione.
        prenotazioneRepository.delete(prenotazione);
    }

    // ============================================
    // Prenotazioni di un utente (storico)
    // ============================================
    public List<PrenotazioneDTO.Response> getByUtente(String telefono) {
        if (!utenteRepository.existsByTelefono(telefono)) {
            throw new ResourceNotFoundException("Utente non trovato con telefono " + telefono);
        }
        return prenotazioneRepository.findByGiocatoreTelefono(telefono).stream()
                .map(this::toResponse)
                .toList();
    }

    // ============================================
    // Prossima prenotazione di un utente, dato il telefono
    // ============================================
    public PrenotazioneDTO.Response getProssimaByTelefono(String telefono) {
        if (!utenteRepository.existsByTelefono(telefono)) {
            throw new ResourceNotFoundException("Utente non trovato con telefono " + telefono);
        }

        LocalDate oggi = LocalDate.now();
        LocalTime oraAttuale = LocalTime.now();

        List<Prenotazione> prossime = prenotazioneRepository.findProssimeByTelefono(telefono, oggi, oraAttuale);

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

    private Utente trovaUtenteSeValorizzato(String telefono) {
        return telefono == null ? null : trovaUtenteOLancia(telefono);
    }

    private PrenotazioneDTO.Response toResponse(Prenotazione p) {
        return new PrenotazioneDTO.Response(
                p.getId(),
                p.getCampo().getId(),
                p.getCampo().getNome(),
                p.getGiocatore1().getTelefono(),
                p.getGiocatore2() != null ? p.getGiocatore2().getTelefono() : null,
                p.getGiocatore3() != null ? p.getGiocatore3().getTelefono() : null,
                p.getGiocatore4() != null ? p.getGiocatore4().getTelefono() : null,
                p.getDataPrenotazione(),
                p.getOraInizio(),
                p.getOraFine(),
                "confermata" // non esiste una colonna stato: se la riga esiste, è attiva
        );
    }
}
