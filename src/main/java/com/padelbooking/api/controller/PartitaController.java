package com.padelbooking.api.controller;

import com.padelbooking.api.dto.PartitaDTO;
import com.padelbooking.api.security.UtentePrincipal;
import com.padelbooking.api.service.PartitaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Operazioni sulla singola partita (dettaglio e, in seguito, le azioni dei partecipanti)
@RestController
@RequestMapping("/api/partite")
public class PartitaController {

    private final PartitaService partitaService;

    public PartitaController(PartitaService partitaService) {
        this.partitaService = partitaService;
    }

    // Bacheca delle partite pubbliche in cui l'utente autenticato può entrare.
    // Un path letterale ha la precedenza su /{id}, quindi "pubbliche" non viene letto come id
    // GET /api/partite/pubbliche
    @GetMapping("/pubbliche")
    public ResponseEntity<List<PartitaDTO.BachecaResponse>> getBachecaPubbliche(
            @AuthenticationPrincipal UtentePrincipal principal) {
        boolean isAdmin = Boolean.TRUE.equals(principal.getUtente().getIsAdmin());
        return ResponseEntity.ok(partitaService.getBachecaPubbliche(principal.getTelefono(), isAdmin));
    }

    // Dettaglio con posti e azioni disponibili per l'utente autenticato
    // GET /api/partite/7
    @GetMapping("/{id}")
    public ResponseEntity<PartitaDTO.DettaglioResponse> getDettaglio(@PathVariable Integer id,
                                                                     @AuthenticationPrincipal UtentePrincipal principal) {
        boolean isAdmin = Boolean.TRUE.equals(principal.getUtente().getIsAdmin());
        return ResponseEntity.ok(partitaService.getDettaglio(id, principal.getTelefono(), isAdmin));
    }

    // Ingresso dalla bacheca, senza body: restituisce il dettaglio già aggiornato
    // POST /api/partite/7/entra
    @PostMapping("/{id}/entra")
    public ResponseEntity<PartitaDTO.DettaglioResponse> entra(@PathVariable Integer id,
                                                              @AuthenticationPrincipal UtentePrincipal principal) {
        boolean isAdmin = Boolean.TRUE.equals(principal.getUtente().getIsAdmin());
        return ResponseEntity.ok(partitaService.entra(id, principal.getTelefono(), isAdmin));
    }
}
