package com.padelbooking.api.controller;

import com.padelbooking.api.config.SecurityConfig;
import com.padelbooking.api.dto.PartitaDTO;
import com.padelbooking.api.model.Utente;
import com.padelbooking.api.security.JwtUtil;
import com.padelbooking.api.security.UtenteDetailsService;
import com.padelbooking.api.security.UtentePrincipal;
import com.padelbooking.api.service.UtenteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UtenteController.class)
@Import(SecurityConfig.class)
class UtenteControllerTest {

    private static final String TEL_1 = "3330000001";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UtenteService utenteService;

    // Dipendenze di SecurityConfig e JwtAuthFilter: senza header Authorization il filtro non le usa
    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UtenteDetailsService utenteDetailsService;

    @Test
    void getInvitabili200ConFormatoDiInvitabiliDellaPartita() throws Exception {
        when(utenteService.getInvitabili("ros", TEL_1)).thenReturn(List.of(
                new PartitaDTO.InvitabileResponse("3330000002", "Mario", "Rossi", 2.5)));

        mockMvc.perform(get("/api/utenti/invitabili").param("cerca", "ros").with(user(principal(TEL_1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].telefono").value("3330000002"))
                .andExpect(jsonPath("$[0].nome").value("Mario"))
                .andExpect(jsonPath("$[0].cognome").value("Rossi"))
                .andExpect(jsonPath("$[0].livello").value(2.5))
                .andExpect(jsonPath("$[0].length()").value(4));
    }

    @Test
    void getInvitabiliSenzaCercaPassaNull() throws Exception {
        when(utenteService.getInvitabili(null, TEL_1)).thenReturn(List.of());

        mockMvc.perform(get("/api/utenti/invitabili").with(user(principal(TEL_1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(utenteService).getInvitabili(isNull(), any());
    }

    @Test
    void getInvitabiliSenzaAutenticazioneRifiutato() throws Exception {
        mockMvc.perform(get("/api/utenti/invitabili")).andExpect(status().isForbidden());

        verify(utenteService, never()).getInvitabili(any(), anyString());
    }

    private static UtentePrincipal principal(String telefono) {
        return new UtentePrincipal(new Utente(telefono, "Nome", "Cognome", "hash", LocalDate.of(1990, 1, 1), false));
    }
}
