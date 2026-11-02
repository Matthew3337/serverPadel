package com.padelbooking.api.controller;

import com.padelbooking.api.dto.ClassificaDTO;
import com.padelbooking.api.security.UtentePrincipal;
import com.padelbooking.api.service.ClassificaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/classifica")
public class ClassificaController {

    private final ClassificaService classificaService;

    public ClassificaController(ClassificaService classificaService) {
        this.classificaService = classificaService;
    }

    // Classifica degli utenti, con evidenziata la riga di chi chiama
    // GET /api/classifica
    @GetMapping
    public ResponseEntity<List<ClassificaDTO.Response>> getClassifica(@AuthenticationPrincipal UtentePrincipal principal) {
        return ResponseEntity.ok(classificaService.getClassifica(principal.getTelefono()));
    }
}
