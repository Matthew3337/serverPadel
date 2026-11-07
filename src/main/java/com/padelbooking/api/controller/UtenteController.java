package com.padelbooking.api.controller;

import com.padelbooking.api.dto.PartitaDTO;
import com.padelbooking.api.security.UtentePrincipal;
import com.padelbooking.api.service.UtenteService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Ricerche sugli utenti per chi gioca ed eliminazione del proprio account.
// La gestione degli utenti da parte dell'admin sta in AdminController
@RestController
@RequestMapping("/api/utenti")
public class UtenteController {

    private final UtenteService utenteService;

    public UtenteController(UtenteService utenteService) {
        this.utenteService = utenteService;
    }

    // Utenti da invitare a una partita ancora da prenotare, filtrati facoltativamente per nome o cognome
    // GET /api/utenti/invitabili?cerca=ros
    @GetMapping("/invitabili")
    public ResponseEntity<List<PartitaDTO.InvitabileResponse>> getInvitabili(
            @RequestParam(required = false) String cerca,
            @AuthenticationPrincipal UtentePrincipal principal) {
        return ResponseEntity.ok(utenteService.getInvitabili(cerca, principal.getTelefono()));
    }

    // Eliminazione del proprio account (utente o admin), senza body nella risposta
    // DELETE /api/utenti/me
    @DeleteMapping("/me")
    public ResponseEntity<Void> eliminaAccount(@AuthenticationPrincipal UtentePrincipal principal) {
        utenteService.eliminaAccount(principal.getTelefono());
        return ResponseEntity.noContent().build();
    }
}
