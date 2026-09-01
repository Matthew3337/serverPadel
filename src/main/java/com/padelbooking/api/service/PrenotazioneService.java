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
import java.util.Set;
import java.util.stream.Collectors;

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

        Set<LocalTime> orariOccupati = prenotazioniEsistenti.stream()
                .map(Prenotazione::getOraInizio)
                .collect(Collectors.toSet());

        List<PrenotazioneDTO.SlotResponse> slots = new ArrayList<>();

        LocalTime cursore = campo.getOraApertura();
        while (true) {
            LocalTime fineSlot = cursore.plusMinutes(DURATA_SLOT_MINUTI);

            // Se sommando 90 minuti si "supera la mezzanotte" tornando a un orario minore,
            // oppure si supera l'orario di chiusura, ci fermiamo
            if (fineSlot.isBefore(cursore) || fineSlot.isAfter(campo.getOraChiusura())) {
                break;
            }

            boolean disponibile = !orariOccupati.contains(cursore);
            slots.add(new PrenotazioneDTO.SlotResponse(cursore, fineSlot, disponibile));

            cursore = fineSlot;
        }

        return slots;
    }

    // ============================================
    // Creazione prenotazione
    // ============================================
    public PrenotazioneDTO.Response create(PrenotazioneDTO.CreateRequest request) {
        Campo campo = campoService.trovaCampoOLancia(request.getIdCampo());

        LocalTime oraInizio = request.getOraInizio();
        LocalTime oraFine = oraInizio.plusMinutes(DURATA_SLOT_MINUTI);

        validaSlotAllInternoOrarioApertura(campo, oraInizio, oraFine);
        validaDataNonPassata(request.getDataPrenotazione(), oraInizio);

        boolean slotOccupato = prenotazioneRepository.existsByCampoIdAndDataPrenotazioneAndOraInizio(
                request.getIdCampo(), request.getDataPrenotazione(), oraInizio);

        if (slotOccupato) {
            throw new BusinessRuleException("Lo slot selezionato è già stato prenotato");
        }

        Prenotazione prenotazione = new Prenotazione();
        prenotazione.setCampo(campo);
        prenotazione.setGiocatore1(trovaUtenteOLancia(request.getIdGiocatore1()));
        prenotazione.setGiocatore2(trovaUtenteSeValorizzato(request.getIdGiocatore2()));
        prenotazione.setGiocatore3(trovaUtenteSeValorizzato(request.getIdGiocatore3()));
        prenotazione.setGiocatore4(trovaUtenteSeValorizzato(request.getIdGiocatore4()));
        prenotazione.setDataPrenotazione(request.getDataPrenotazione());
        prenotazione.setOraInizio(oraInizio);
        prenotazione.setOraFine(oraFine);

        Prenotazione salvata = prenotazioneRepository.save(prenotazione);
        return toResponse(salvata);
    }

    // ============================================
    // Cancellazione prenotazione
    // ============================================
    public void cancel(Integer idPrenotazione, Integer idUtenteRichiedente, boolean isAdmin) {
        Prenotazione prenotazione = prenotazioneRepository.findById(idPrenotazione)
                .orElseThrow(() -> new ResourceNotFoundException("Prenotazione non trovata con id " + idPrenotazione));

        boolean isProprietario = prenotazione.getGiocatore1().getId().equals(idUtenteRichiedente);
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
    public List<PrenotazioneDTO.Response> getByUtente(Integer idUtente) {
        return prenotazioneRepository.findByGiocatoreId(idUtente).stream()
                .map(this::toResponse)
                .toList();
    }

    // ============================================
    // Helper privati
    // ============================================
    private void validaSlotAllInternoOrarioApertura(Campo campo, LocalTime oraInizio, LocalTime oraFine) {
        boolean fuoriOrario = oraInizio.isBefore(campo.getOraApertura())
                || oraFine.isAfter(campo.getOraChiusura())
                || oraFine.isBefore(oraInizio); // copre il caso di overflow oltre mezzanotte

        if (fuoriOrario) {
            throw new BusinessRuleException("L'orario richiesto è fuori dalla fascia di apertura del campo");
        }
    }

    private void validaDataNonPassata(LocalDate data, LocalTime oraInizio) {
        LocalDateTime inizio = LocalDateTime.of(data, oraInizio);
        if (inizio.isBefore(LocalDateTime.now())) {
            throw new BusinessRuleException("Non è possibile prenotare uno slot nel passato");
        }
    }

    private Utente trovaUtenteOLancia(Integer id) {
        return utenteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato con id " + id));
    }

    private Utente trovaUtenteSeValorizzato(Integer id) {
        return id == null ? null : trovaUtenteOLancia(id);
    }

    private PrenotazioneDTO.Response toResponse(Prenotazione p) {
        return new PrenotazioneDTO.Response(
                p.getId(),
                p.getCampo().getId(),
                p.getCampo().getNome(),
                p.getGiocatore1().getId(),
                p.getGiocatore2() != null ? p.getGiocatore2().getId() : null,
                p.getGiocatore3() != null ? p.getGiocatore3().getId() : null,
                p.getGiocatore4() != null ? p.getGiocatore4().getId() : null,
                p.getDataPrenotazione(),
                p.getOraInizio(),
                p.getOraFine(),
                "confermata" // non esiste una colonna stato: se la riga esiste, è attiva
        );
    }
}
