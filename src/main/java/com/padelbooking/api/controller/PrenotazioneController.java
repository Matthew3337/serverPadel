package com.padelbooking.api.controller;

import com.padelbooking.api.dto.PrenotazioneDTO;
import com.padelbooking.api.security.UtentePrincipal;
import com.padelbooking.api.service.PrenotazioneService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/prenotazioni")
public class PrenotazioneController {

    private final PrenotazioneService prenotazioneService;

    public PrenotazioneController(PrenotazioneService prenotazioneService) {
        this.prenotazioneService = prenotazioneService;
    }

    // GET /api/prenotazioni/slot-disponibili?idCampo=1&data=2026-07-10
    @GetMapping("/slot-disponibili")
    public ResponseEntity<List<PrenotazioneDTO.SlotResponse>> getSlotDisponibili(
            @RequestParam Integer idCampo,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return ResponseEntity.ok(prenotazioneService.getSlotDisponibili(idCampo, data));
    }

    @PostMapping
    public ResponseEntity<PrenotazioneDTO.Response> create(@Valid @RequestBody PrenotazioneDTO.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(prenotazioneService.create(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable Integer id, @AuthenticationPrincipal UtentePrincipal principal) {
        boolean isAdmin = principal.getUtente().getIsAdmin();
        prenotazioneService.cancel(id, principal.getId(), isAdmin);
        return ResponseEntity.noContent().build();
    }

    // Storico prenotazioni dell'utente autenticato
    @GetMapping("/mie")
    public ResponseEntity<List<PrenotazioneDTO.Response>> getMiePrenotazioni(@AuthenticationPrincipal UtentePrincipal principal) {
        return ResponseEntity.ok(prenotazioneService.getByUtente(principal.getId()));
    }

    // Storico prenotazioni di un utente specifico (utile lato admin)
    @GetMapping("/utente/{idUtente}")
    public ResponseEntity<List<PrenotazioneDTO.Response>> getByUtente(@PathVariable Integer idUtente) {
        return ResponseEntity.ok(prenotazioneService.getByUtente(idUtente));
    }
}
