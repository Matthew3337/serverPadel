package com.padelbooking.api.controller;

import com.padelbooking.api.dto.CampoDTO;
import com.padelbooking.api.service.CampoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/campi")
public class CampoController {

    private final CampoService campoService;

    public CampoController(CampoService campoService) {
        this.campoService = campoService;
    }

    // Pubblico: chiunque può vedere l'elenco dei campi
    @GetMapping
    public ResponseEntity<List<CampoDTO.Response>> getAll() {
        return ResponseEntity.ok(campoService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CampoDTO.Response> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(campoService.getById(id));
    }

    // Da qui in poi solo ADMIN (regola già applicata in SecurityConfig)
    @PostMapping
    public ResponseEntity<CampoDTO.Response> create(@Valid @RequestBody CampoDTO.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campoService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CampoDTO.Response> update(@PathVariable Integer id, @Valid @RequestBody CampoDTO.Request request) {
        return ResponseEntity.ok(campoService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        campoService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
