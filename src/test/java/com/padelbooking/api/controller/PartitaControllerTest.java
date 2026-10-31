package com.padelbooking.api.controller;

import com.padelbooking.api.config.SecurityConfig;
import com.padelbooking.api.dto.PartitaDTO;
import com.padelbooking.api.exception.AccessoNegatoException;
import com.padelbooking.api.exception.BusinessRuleException;
import com.padelbooking.api.exception.ResourceNotFoundException;
import com.padelbooking.api.model.Utente;
import com.padelbooking.api.security.JwtUtil;
import com.padelbooking.api.security.UtenteDetailsService;
import com.padelbooking.api.security.UtentePrincipal;
import com.padelbooking.api.service.PartitaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PartitaController.class)
@Import(SecurityConfig.class)
class PartitaControllerTest {

    private static final String TEL_1 = "3330000001";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PartitaService partitaService;

    // Dipendenze di SecurityConfig e JwtAuthFilter: senza header Authorization il filtro non le usa
    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UtenteDetailsService utenteDetailsService;

    @Test
    void getDettaglio200ConFormatoDelContratto() throws Exception {
        PartitaDTO.DettaglioResponse r = new PartitaDTO.DettaglioResponse(
                7, 1, "Campo 1", LocalDate.of(2026, 11, 5), LocalTime.of(18, 0), LocalTime.of(19, 30),
                "creata", "PUBBLICA", TEL_1,
                List.of(new PartitaDTO.PostoResponse("UTENTE", TEL_1, "Mario", "Rossi", 2.5, false),
                        new PartitaDTO.PostoResponse("CAMPO", null, null, null, null, false)),
                new PartitaDTO.AzioniResponse(true, true, false, false, false));
        when(partitaService.getDettaglio(7, TEL_1, false)).thenReturn(r);

        mockMvc.perform(get("/api/partite/7").with(user(principal(TEL_1, false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.idCampo").value(1))
                .andExpect(jsonPath("$.nomeCampo").value("Campo 1"))
                .andExpect(jsonPath("$.dataPartita").value("2026-11-05"))
                .andExpect(jsonPath("$.oraInizio").value("18:00:00"))
                .andExpect(jsonPath("$.oraFine").value("19:30:00"))
                .andExpect(jsonPath("$.stato").value("creata"))
                .andExpect(jsonPath("$.tipo").value("PUBBLICA"))
                .andExpect(jsonPath("$.telefonoCreatore").value(TEL_1))
                .andExpect(jsonPath("$.posti[0].tipo").value("UTENTE"))
                .andExpect(jsonPath("$.posti[0].livello").value(2.5))
                .andExpect(jsonPath("$.posti[0].confermata").value(false))
                .andExpect(jsonPath("$.posti[1].tipo").value("CAMPO"))
                .andExpect(jsonPath("$.posti[1].telefono").value(nullValue()))
                .andExpect(jsonPath("$.azioni.puoInvitare").value(true))
                .andExpect(jsonPath("$.azioni.puoEliminare").value(true))
                .andExpect(jsonPath("$.azioni.puoUscire").value(false))
                .andExpect(jsonPath("$.azioni.puoConfermare").value(false))
                .andExpect(jsonPath("$.azioni.puoRegistrareRisultato").value(false));
    }

    @Test
    void getDettaglioPassaIlFlagAdmin() throws Exception {
        when(partitaService.getDettaglio(eq(7), eq(TEL_1), eq(true))).thenReturn(new PartitaDTO.DettaglioResponse());

        mockMvc.perform(get("/api/partite/7").with(user(principal(TEL_1, true))))
                .andExpect(status().isOk());

        verify(partitaService).getDettaglio(7, TEL_1, true);
    }

    @Test
    void getDettaglioNonTrovata404ConFormatoErrore() throws Exception {
        when(partitaService.getDettaglio(7, TEL_1, false))
                .thenThrow(new ResourceNotFoundException("Partita non trovata con id 7"));

        mockMvc.perform(get("/api/partite/7").with(user(principal(TEL_1, false))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").value(notNullValue()))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.errore").value("Partita non trovata con id 7"))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void getDettaglioSenzaAutenticazioneRifiutato() throws Exception {
        mockMvc.perform(get("/api/partite/7")).andExpect(status().isForbidden());

        verify(partitaService, never()).getDettaglio(anyInt(), anyString(), anyBoolean());
    }

    // ============================================
    // Bacheca pubbliche
    // ============================================

    @Test
    void getBacheca200ConFormatoDelContrattoENonPassaDalDettaglio() throws Exception {
        PartitaDTO.BachecaResponse riga = new PartitaDTO.BachecaResponse(
                7, 1, "Campo 1", LocalDate.of(2026, 11, 5), LocalTime.of(18, 0), LocalTime.of(19, 30),
                "Mario", "Rossi", 2.5, 3);
        when(partitaService.getBachecaPubbliche(TEL_1, false)).thenReturn(List.of(riga));

        mockMvc.perform(get("/api/partite/pubbliche").with(user(principal(TEL_1, false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].idCampo").value(1))
                .andExpect(jsonPath("$[0].nomeCampo").value("Campo 1"))
                .andExpect(jsonPath("$[0].dataPartita").value("2026-11-05"))
                .andExpect(jsonPath("$[0].oraInizio").value("18:00:00"))
                .andExpect(jsonPath("$[0].oraFine").value("19:30:00"))
                .andExpect(jsonPath("$[0].nomeCreatore").value("Mario"))
                .andExpect(jsonPath("$[0].cognomeCreatore").value("Rossi"))
                .andExpect(jsonPath("$[0].livelloCreatore").value(2.5))
                .andExpect(jsonPath("$[0].postiLiberi").value(3))
                .andExpect(jsonPath("$[0].length()").value(10));

        verify(partitaService, never()).getDettaglio(anyInt(), anyString(), anyBoolean());
    }

    @Test
    void getBachecaVuotaPerAdmin() throws Exception {
        when(partitaService.getBachecaPubbliche(TEL_1, true)).thenReturn(List.of());

        mockMvc.perform(get("/api/partite/pubbliche").with(user(principal(TEL_1, true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getBachecaSenzaAutenticazioneRifiutata() throws Exception {
        mockMvc.perform(get("/api/partite/pubbliche")).andExpect(status().isForbidden());

        verify(partitaService, never()).getBachecaPubbliche(anyString(), anyBoolean());
    }

    // ============================================
    // Ingresso
    // ============================================

    @Test
    void entra200ConDettaglioAggiornato() throws Exception {
        PartitaDTO.DettaglioResponse r = new PartitaDTO.DettaglioResponse(
                7, 1, "Campo 1", LocalDate.of(2026, 11, 5), LocalTime.of(18, 0), LocalTime.of(19, 30),
                "creata", "PUBBLICA", "3330000009",
                List.of(new PartitaDTO.PostoResponse("UTENTE", "3330000009", "Anna", "Bianchi", 2.0, false),
                        new PartitaDTO.PostoResponse("UTENTE", TEL_1, "Nome", "Cognome", 1.0, false)),
                new PartitaDTO.AzioniResponse(false, false, true, false, false));
        when(partitaService.entra(7, TEL_1, false)).thenReturn(r);

        mockMvc.perform(post("/api/partite/7/entra").with(user(principal(TEL_1, false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.posti.length()").value(2))
                .andExpect(jsonPath("$.posti[1].telefono").value(TEL_1))
                .andExpect(jsonPath("$.azioni.puoUscire").value(true));
    }

    @Test
    void entra409ConMessaggioDellaRegola() throws Exception {
        when(partitaService.entra(7, TEL_1, false))
                .thenThrow(new BusinessRuleException("La partita è al completo"));

        mockMvc.perform(post("/api/partite/7/entra").with(user(principal(TEL_1, false))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.timestamp").value(notNullValue()))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errore").value("La partita è al completo"))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void entra404ConFormatoErrore() throws Exception {
        when(partitaService.entra(7, TEL_1, false))
                .thenThrow(new ResourceNotFoundException("Partita non trovata con id 7"));

        mockMvc.perform(post("/api/partite/7/entra").with(user(principal(TEL_1, false))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.errore").value("Partita non trovata con id 7"));
    }

    @Test
    void entra403PerAdminConFormatoErrore() throws Exception {
        when(partitaService.entra(7, TEL_1, true))
                .thenThrow(new AccessoNegatoException("L'amministratore non può entrare nelle partite"));

        mockMvc.perform(post("/api/partite/7/entra").with(user(principal(TEL_1, true))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.timestamp").value(notNullValue()))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errore").value("L'amministratore non può entrare nelle partite"))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void entraSenzaAutenticazioneRifiutato() throws Exception {
        mockMvc.perform(post("/api/partite/7/entra")).andExpect(status().isForbidden());

        verify(partitaService, never()).entra(anyInt(), anyString(), anyBoolean());
    }

    private static UtentePrincipal principal(String telefono, boolean admin) {
        return new UtentePrincipal(new Utente(telefono, "Nome", "Cognome", "hash", LocalDate.of(1990, 1, 1), admin));
    }
}
