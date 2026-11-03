package com.padelbooking.api.controller;

import com.padelbooking.api.dto.AdminDTO;
import com.padelbooking.api.dto.PartitaDTO;
import com.padelbooking.api.security.UtentePrincipal;
import com.padelbooking.api.service.AdminService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

// Area amministratore: partite del giorno, avvisi, livelli degli utenti e correzioni sulle partite.
// L'accesso riservato all'admin sta in SecurityConfig, su tutto /api/admin/**.
// Niente @Valid: i dati li controlla il service (vedi AdminDTO)
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // Partite di una data su tutti i campi, anche quelli senza partite, annullate comprese
    // GET /api/admin/partite?data=2026-11-05
    @GetMapping("/partite")
    public ResponseEntity<List<AdminDTO.CampoPartiteResponse>> getPartiteDelGiorno(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return ResponseEntity.ok(adminService.getPartiteDelGiorno(data));
    }

    // Avvisi delle partite non annullate e non ancora iniziate
    // GET /api/admin/avvisi
    @GetMapping("/avvisi")
    public ResponseEntity<List<AdminDTO.AvvisoResponse>> getAvvisi() {
        return ResponseEntity.ok(adminService.getAvvisi());
    }

    // Utenti non admin, filtrati facoltativamente per nome o cognome
    // GET /api/admin/utenti?cerca=ros
    @GetMapping("/utenti")
    public ResponseEntity<List<AdminDTO.UtenteResponse>> getUtenti(@RequestParam(required = false) String cerca) {
        return ResponseEntity.ok(adminService.getUtenti(cerca));
    }

    // PUT /api/admin/utenti/3330000001/livello  { "livello": 2.5 }
    @PutMapping("/utenti/{telefono}/livello")
    public ResponseEntity<AdminDTO.UtenteResponse> modificaLivello(@PathVariable String telefono,
                                                                   @RequestBody AdminDTO.LivelloRequest request) {
        return ResponseEntity.ok(adminService.modificaLivello(telefono, request.getLivello()));
    }

    // Sostituisce i partecipanti UTENTE con l'elenco indicato; i posti CAMPO restano
    // PUT /api/admin/partite/7/partecipanti  { "telefoni": ["3330000001", "3330000002"] }
    @PutMapping("/partite/{id}/partecipanti")
    public ResponseEntity<PartitaDTO.DettaglioResponse> modificaPartecipanti(
            @PathVariable Integer id,
            @RequestBody AdminDTO.PartecipantiRequest request) {
        return ResponseEntity.ok(adminService.modificaPartecipanti(id, request.getTelefoni()));
    }

    // Registrazione o correzione del risultato: stesso body di POST /api/partite/{id}/risultato
    // PUT /api/admin/partite/7/risultato  { "squadra1": [11, 12], "squadra2": [13, 14], "set": [ { "squadra1": 6, "squadra2": 4 }, ... ] }
    @PutMapping("/partite/{id}/risultato")
    public ResponseEntity<PartitaDTO.DettaglioResponse> modificaRisultato(
            @PathVariable Integer id,
            @RequestBody PartitaDTO.RisultatoRequest request,
            @AuthenticationPrincipal UtentePrincipal principal) {
        return ResponseEntity.ok(adminService.modificaRisultato(id, request, principal.getTelefono()));
    }

    // PUT /api/admin/partite/7/stato  { "stato": "annullata" }
    @PutMapping("/partite/{id}/stato")
    public ResponseEntity<PartitaDTO.DettaglioResponse> modificaStato(@PathVariable Integer id,
                                                                      @RequestBody AdminDTO.StatoRequest request) {
        return ResponseEntity.ok(adminService.modificaStato(id, request.getStato()));
    }

    // Annullamento in qualsiasi momento, senza body
    // POST /api/admin/partite/7/annulla
    @PostMapping("/partite/{id}/annulla")
    public ResponseEntity<PartitaDTO.DettaglioResponse> annulla(@PathVariable Integer id) {
        return ResponseEntity.ok(adminService.annulla(id));
    }
}
