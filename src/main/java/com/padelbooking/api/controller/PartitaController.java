package com.padelbooking.api.controller;

import com.padelbooking.api.dto.PartitaDTO;
import com.padelbooking.api.security.UtentePrincipal;
import com.padelbooking.api.service.PartitaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Operazioni sulla singola partita: dettaglio, bacheche, storico, inviti e azioni dei partecipanti
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

    // Storico delle partite non annullate dell'utente autenticato, dalla più recente.
    // Come "pubbliche", il path letterale ha la precedenza su /{id}
    // GET /api/partite/storico
    @GetMapping("/storico")
    public ResponseEntity<List<PartitaDTO.StoricoResponse>> getStorico(
            @AuthenticationPrincipal UtentePrincipal principal) {
        boolean isAdmin = Boolean.TRUE.equals(principal.getUtente().getIsAdmin());
        return ResponseEntity.ok(partitaService.getStorico(principal.getTelefono(), isAdmin));
    }

    // Bacheca inviti: le partite creata o confermata in cui l'utente autenticato è stato invitato.
    // Come "pubbliche", il path letterale ha la precedenza su /{id}
    // GET /api/partite/inviti
    @GetMapping("/inviti")
    public ResponseEntity<List<PartitaDTO.InvitoRicevutoResponse>> getBachecaInviti(
            @AuthenticationPrincipal UtentePrincipal principal) {
        boolean isAdmin = Boolean.TRUE.equals(principal.getUtente().getIsAdmin());
        return ResponseEntity.ok(partitaService.getBachecaInviti(principal.getTelefono(), isAdmin));
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

    // Eliminazione da parte del creatore: la partita passa ad annullata, nessun body nella risposta
    // DELETE /api/partite/7
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> elimina(@PathVariable Integer id,
                                        @AuthenticationPrincipal UtentePrincipal principal) {
        boolean isAdmin = Boolean.TRUE.equals(principal.getUtente().getIsAdmin());
        partitaService.elimina(id, principal.getTelefono(), isAdmin);
        return ResponseEntity.noContent().build();
    }

    // Uscita di un partecipante diverso dal creatore, senza body: il posto si libera
    // POST /api/partite/7/esci
    @PostMapping("/{id}/esci")
    public ResponseEntity<Void> esci(@PathVariable Integer id,
                                     @AuthenticationPrincipal UtentePrincipal principal) {
        boolean isAdmin = Boolean.TRUE.equals(principal.getUtente().getIsAdmin());
        partitaService.esci(id, principal.getTelefono(), isAdmin);
        return ResponseEntity.noContent().build();
    }

    // Utenti che il creatore può invitare, filtrati facoltativamente per nome o cognome
    // GET /api/partite/7/invitabili?cerca=ros
    @GetMapping("/{id}/invitabili")
    public ResponseEntity<List<PartitaDTO.InvitabileResponse>> getInvitabili(
            @PathVariable Integer id,
            @RequestParam(required = false) String cerca,
            @AuthenticationPrincipal UtentePrincipal principal) {
        boolean isAdmin = Boolean.TRUE.equals(principal.getUtente().getIsAdmin());
        return ResponseEntity.ok(partitaService.getInvitabili(id, cerca, principal.getTelefono(), isAdmin));
    }

    // Invito da parte del creatore: l'utente entra subito nella partita, restituisce il dettaglio aggiornato
    // POST /api/partite/7/inviti  { "telefono": "333..." }
    @PostMapping("/{id}/inviti")
    public ResponseEntity<PartitaDTO.DettaglioResponse> invita(@PathVariable Integer id,
                                                               @Valid @RequestBody PartitaDTO.InvitoRequest request,
                                                               @AuthenticationPrincipal UtentePrincipal principal) {
        boolean isAdmin = Boolean.TRUE.equals(principal.getUtente().getIsAdmin());
        return ResponseEntity.ok(partitaService.invita(id, request.getTelefono(), principal.getTelefono(), isAdmin));
    }
}
