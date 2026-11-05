package com.padelbooking.api.controller;

import com.padelbooking.api.config.SecurityConfig;
import com.padelbooking.api.exception.BusinessRuleException;
import com.padelbooking.api.model.Utente;
import com.padelbooking.api.security.JwtUtil;
import com.padelbooking.api.security.UtenteDetailsService;
import com.padelbooking.api.security.UtentePrincipal;
import com.padelbooking.api.service.CampoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CampoController.class)
@Import(SecurityConfig.class)
class CampoControllerTest {

    private static final String MESSAGGIO = "Il campo ha partite collegate e non può essere eliminato";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CampoService campoService;

    // Dipendenze di SecurityConfig e JwtAuthFilter: senza header Authorization il filtro non le usa
    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UtenteDetailsService utenteDetailsService;

    @Test
    void eliminazione204() throws Exception {
        mockMvc.perform(delete("/api/campi/1").with(user(principal(true)))).andExpect(status().isNoContent());
        verify(campoService).delete(1);
    }

    @Test
    void conPartiteCollegate409() throws Exception {
        doThrow(new BusinessRuleException(MESSAGGIO)).when(campoService).delete(1);

        mockMvc.perform(delete("/api/campi/1").with(user(principal(true))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errore").value(MESSAGGIO));
    }

    @Test
    void utenteNormale403() throws Exception {
        mockMvc.perform(delete("/api/campi/1").with(user(principal(false)))).andExpect(status().isForbidden());
        verifyNoInteractions(campoService);
    }

    private static UtentePrincipal principal(boolean admin) {
        return new UtentePrincipal(new Utente("3330000100", "Mario", "Rossi", "x", LocalDate.of(1990, 1, 1), admin));
    }
}
