package com.padelbooking.api.controller;

import com.padelbooking.api.config.SecurityConfig;
import com.padelbooking.api.dto.ClassificaDTO;
import com.padelbooking.api.model.Utente;
import com.padelbooking.api.security.JwtUtil;
import com.padelbooking.api.security.UtenteDetailsService;
import com.padelbooking.api.security.UtentePrincipal;
import com.padelbooking.api.service.ClassificaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClassificaController.class)
@Import(SecurityConfig.class)
class ClassificaControllerTest {

    private static final String TEL_UTENTE = "3330000001";
    private static final String TEL_ADMIN = "3330000100";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClassificaService classificaService;

    // Dipendenze di SecurityConfig e JwtAuthFilter: senza header Authorization il filtro non le usa
    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UtenteDetailsService utenteDetailsService;

    @Test
    void get200ConFormatoDelContratto() throws Exception {
        when(classificaService.getClassifica(TEL_UTENTE)).thenReturn(List.of(
                new ClassificaDTO.Response(1, TEL_UTENTE, "Mario", "Rossi", 3, true),
                new ClassificaDTO.Response(2, "3330000002", "Luigi", "Verdi", 1, false)));

        mockMvc.perform(get("/api/classifica").with(user(principal(TEL_UTENTE, false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].length()").value(6))
                .andExpect(jsonPath("$[0].posizione").value(1))
                .andExpect(jsonPath("$[0].telefono").value(TEL_UTENTE))
                .andExpect(jsonPath("$[0].nome").value("Mario"))
                .andExpect(jsonPath("$[0].cognome").value("Rossi"))
                .andExpect(jsonPath("$[0].punteggio").value(3))
                .andExpect(jsonPath("$[0].utenteCorrente").value(true))
                .andExpect(jsonPath("$[1].utenteCorrente").value(false));
    }

    @Test
    void listaVuota() throws Exception {
        when(classificaService.getClassifica(TEL_UTENTE)).thenReturn(List.of());

        mockMvc.perform(get("/api/classifica").with(user(principal(TEL_UTENTE, false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void adminRiceveLoStessoElencoConUtenteCorrenteFalse() throws Exception {
        when(classificaService.getClassifica(TEL_ADMIN)).thenReturn(List.of(
                new ClassificaDTO.Response(1, TEL_UTENTE, "Mario", "Rossi", 3, false)));

        mockMvc.perform(get("/api/classifica").with(user(principal(TEL_ADMIN, true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].telefono").value(TEL_UTENTE))
                .andExpect(jsonPath("$[0].utenteCorrente").value(false));
    }

    @Test
    void senzaToken403() throws Exception {
        mockMvc.perform(get("/api/classifica")).andExpect(status().isForbidden());
        verifyNoInteractions(classificaService);
    }

    private static UtentePrincipal principal(String telefono, boolean admin) {
        return new UtentePrincipal(new Utente(telefono, "Mario", "Rossi", "x", LocalDate.of(1990, 1, 1), admin));
    }
}
