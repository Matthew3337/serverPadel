package com.padelbooking.api.controller;

import com.padelbooking.api.config.SecurityConfig;
import com.padelbooking.api.dto.AdminDTO;
import com.padelbooking.api.dto.PartitaDTO;
import com.padelbooking.api.exception.BusinessRuleException;
import com.padelbooking.api.exception.ResourceNotFoundException;
import com.padelbooking.api.exception.RichiestaNonValidaException;
import com.padelbooking.api.model.Utente;
import com.padelbooking.api.security.JwtUtil;
import com.padelbooking.api.security.UtenteDetailsService;
import com.padelbooking.api.security.UtentePrincipal;
import com.padelbooking.api.service.AdminService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerTest {

    private static final String TEL_UTENTE = "3330000001";
    private static final String TEL_ADMIN = "3330000100";
    private static final String BODY_RISULTATO = """
            { "squadra1": [11, 12], "squadra2": [13, 14],
              "set": [ { "squadra1": 6, "squadra2": 4 }, { "squadra1": 6, "squadra2": 3 } ] }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminService adminService;

    // Dipendenze di SecurityConfig e JwtAuthFilter: senza header Authorization il filtro non le usa
    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UtenteDetailsService utenteDetailsService;

    // ============================================
    // Ruoli
    // ============================================

    @ParameterizedTest
    @CsvSource({
            "GET, /api/admin/partite?data=2026-11-05",
            "GET, /api/admin/avvisi",
            "GET, /api/admin/utenti",
            "PUT, /api/admin/utenti/3330000002/livello",
            "PUT, /api/admin/partite/7/partecipanti",
            "PUT, /api/admin/partite/7/risultato",
            "PUT, /api/admin/partite/7/stato",
            "POST, /api/admin/partite/7/annulla",
            // Anche un path senza controller è riservato: non si rivela cosa esiste
            "GET, /api/admin/altro"
    })
    void utenteNormale403ESenzaToken403(String metodo, String path) throws Exception {
        MockHttpServletRequestBuilder richiesta = request(HttpMethod.valueOf(metodo), path)
                .contentType(MediaType.APPLICATION_JSON).content("{}");

        mockMvc.perform(richiesta.with(user(principal(TEL_UTENTE, false)))).andExpect(status().isForbidden());
        mockMvc.perform(request(HttpMethod.valueOf(metodo), path)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());

        verifyNoInteractions(adminService);
    }

    // ============================================
    // Partite del giorno
    // ============================================

    @Test
    void partiteDelGiornoFormatoDelContratto() throws Exception {
        LocalDate data = LocalDate.of(2026, 11, 5);
        when(adminService.getPartiteDelGiorno(data)).thenReturn(List.of(
                new AdminDTO.CampoPartiteResponse(1, "Campo Centrale", List.of(
                        new AdminDTO.PartitaResponse(7, LocalTime.of(18, 30), LocalTime.of(20, 0), "annullata", "PUBBLICA",
                                List.of("Mario Rossi", "Giocatore del campo"), 2,
                                List.of(new PartitaDTO.SetResponse(6, 4))))),
                new AdminDTO.CampoPartiteResponse(2, "Campo Nord", List.of())));

        asAdmin(get("/api/admin/partite").param("data", "2026-11-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].length()").value(3))
                .andExpect(jsonPath("$[0].idCampo").value(1))
                .andExpect(jsonPath("$[0].nomeCampo").value("Campo Centrale"))
                .andExpect(jsonPath("$[0].partite[0].length()").value(8))
                .andExpect(jsonPath("$[0].partite[0].id").value(7))
                .andExpect(jsonPath("$[0].partite[0].oraInizio").value("18:30:00"))
                .andExpect(jsonPath("$[0].partite[0].oraFine").value("20:00:00"))
                .andExpect(jsonPath("$[0].partite[0].stato").value("annullata"))
                .andExpect(jsonPath("$[0].partite[0].tipo").value("PUBBLICA"))
                .andExpect(jsonPath("$[0].partite[0].giocatori[1]").value("Giocatore del campo"))
                .andExpect(jsonPath("$[0].partite[0].postiLiberi").value(2))
                .andExpect(jsonPath("$[0].partite[0].risultato[0].squadra1").value(6))
                .andExpect(jsonPath("$[0].partite[0].risultato[0].squadra2").value(4))
                .andExpect(jsonPath("$[1].partite.length()").value(0));
    }

    @Test
    void partiteDelGiornoRisultatoNullPresenteNelJson() throws Exception {
        when(adminService.getPartiteDelGiorno(any())).thenReturn(List.of(
                new AdminDTO.CampoPartiteResponse(1, "C", List.of(
                        new AdminDTO.PartitaResponse(7, LocalTime.of(18, 30), LocalTime.of(20, 0), "creata", "PRIVATA",
                                List.of(), 4, null)))));

        asAdmin(get("/api/admin/partite").param("data", "2026-11-05"))
                .andExpect(jsonPath("$[0].partite[0].length()").value(8))
                .andExpect(jsonPath("$[0].partite[0].risultato").doesNotExist());
    }

    @Test
    void dataMancante400() throws Exception {
        asAdmin(get("/api/admin/partite"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errore").value("Parametro obbligatorio mancante: data"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void dataNonValida400() throws Exception {
        asAdmin(get("/api/admin/partite").param("data", "2026-13-45"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("Valore non valido per il parametro data"));
        verifyNoInteractions(adminService);
    }

    // ============================================
    // Avvisi e utenti
    // ============================================

    @Test
    void avvisiFormatoDelContratto() throws Exception {
        when(adminService.getAvvisi()).thenReturn(List.of(new AdminDTO.AvvisoResponse(3, 7, "Campo Centrale",
                LocalDate.of(2026, 11, 5), LocalTime.of(18, 30), LocalTime.of(20, 0), 2)));

        asAdmin(get("/api/admin/avvisi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].length()").value(7))
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].idPartita").value(7))
                .andExpect(jsonPath("$[0].nomeCampo").value("Campo Centrale"))
                .andExpect(jsonPath("$[0].dataPartita").value("2026-11-05"))
                .andExpect(jsonPath("$[0].oraInizio").value("18:30:00"))
                .andExpect(jsonPath("$[0].oraFine").value("20:00:00"))
                .andExpect(jsonPath("$[0].postiLiberi").value(2));
    }

    @Test
    void utentiFormatoDelContratto() throws Exception {
        when(adminService.getUtenti("ros")).thenReturn(List.of(
                new AdminDTO.UtenteResponse(TEL_UTENTE, "Mario", "Rossi", 2.5, 7)));

        asAdmin(get("/api/admin/utenti").param("cerca", "ros"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].length()").value(5))
                .andExpect(jsonPath("$[0].telefono").value(TEL_UTENTE))
                .andExpect(jsonPath("$[0].nome").value("Mario"))
                .andExpect(jsonPath("$[0].cognome").value("Rossi"))
                .andExpect(jsonPath("$[0].livello").value(2.5))
                .andExpect(jsonPath("$[0].punteggio").value(7));
    }

    @Test
    void utentiSenzaCerca() throws Exception {
        when(adminService.getUtenti(null)).thenReturn(List.of());

        asAdmin(get("/api/admin/utenti"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ============================================
    // Livello
    // ============================================

    @Test
    void livello200() throws Exception {
        when(adminService.modificaLivello(TEL_UTENTE, 2.5)).thenReturn(
                new AdminDTO.UtenteResponse(TEL_UTENTE, "Mario", "Rossi", 2.5, 7));

        asAdmin(jsonPut("/api/admin/utenti/" + TEL_UTENTE + "/livello", "{ \"livello\": 2.5 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.livello").value(2.5))
                .andExpect(jsonPath("$.punteggio").value(7));
    }

    @Test
    void livelloNonValido400ConFormatoErrore() throws Exception {
        when(adminService.modificaLivello(TEL_UTENTE, 9.0))
                .thenThrow(new RichiestaNonValidaException("Il livello deve essere tra 0 e 5 con passo 0,5"));

        asAdmin(jsonPut("/api/admin/utenti/" + TEL_UTENTE + "/livello", "{ \"livello\": 9 }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errore").value("Il livello deve essere tra 0 e 5 con passo 0,5"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void livelloUtenteNonTrovato404() throws Exception {
        when(adminService.modificaLivello(TEL_UTENTE, 2.0)).thenThrow(new ResourceNotFoundException("Utente non trovato"));

        asAdmin(jsonPut("/api/admin/utenti/" + TEL_UTENTE + "/livello", "{ \"livello\": 2 }"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errore").value("Utente non trovato"));
    }

    @Test
    void bodyNonValido400() throws Exception {
        asAdmin(jsonPut("/api/admin/utenti/" + TEL_UTENTE + "/livello", "{ non json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(adminService);
    }

    // ============================================
    // Operazioni sulle partite
    // ============================================

    @Test
    void partecipanti200ConDettaglio() throws Exception {
        when(adminService.modificaPartecipanti(7, List.of(TEL_UTENTE, "3330000002"))).thenReturn(dettaglio("creata"));

        asAdmin(jsonPut("/api/admin/partite/7/partecipanti", "{ \"telefoni\": [\"3330000001\", \"3330000002\"] }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.stato").value("creata"))
                .andExpect(jsonPath("$.azioni.puoInvitare").value(false));
    }

    @Test
    void partecipantiErrori409() throws Exception {
        when(adminService.modificaPartecipanti(eq(7), any()))
                .thenThrow(new BusinessRuleException("Il creatore non può essere rimosso: annulla la partita"));

        asAdmin(jsonPut("/api/admin/partite/7/partecipanti", "{ \"telefoni\": [\"3330000002\"] }"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errore").value("Il creatore non può essere rimosso: annulla la partita"));
    }

    @Test
    void risultato200PassaIlBodyEIlTelefonoDellAdmin() throws Exception {
        when(adminService.modificaRisultato(eq(7), any(), eq(TEL_ADMIN))).thenReturn(dettaglio("giocata"));

        asAdmin(jsonPut("/api/admin/partite/7/risultato", BODY_RISULTATO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato").value("giocata"));

        ArgumentCaptor<PartitaDTO.RisultatoRequest> body = ArgumentCaptor.forClass(PartitaDTO.RisultatoRequest.class);
        verify(adminService).modificaRisultato(eq(7), body.capture(), eq(TEL_ADMIN));
        assertThat(body.getValue().getSquadra1()).containsExactly(11, 12);
        assertThat(body.getValue().getSquadra2()).containsExactly(13, 14);
        assertThat(body.getValue().getSet()).hasSize(2);
    }

    @Test
    void risultatoPartitaInesistente404() throws Exception {
        when(adminService.modificaRisultato(eq(7), any(), anyString()))
                .thenThrow(new ResourceNotFoundException("Partita non trovata con id 7"));

        asAdmin(jsonPut("/api/admin/partite/7/risultato", BODY_RISULTATO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errore").value("Partita non trovata con id 7"));
    }

    @Test
    void stato200() throws Exception {
        when(adminService.modificaStato(7, "annullata")).thenReturn(dettaglio("annullata"));

        asAdmin(jsonPut("/api/admin/partite/7/stato", "{ \"stato\": \"annullata\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato").value("annullata"));
    }

    @Test
    void statoSlotOccupato409() throws Exception {
        when(adminService.modificaStato(7, "creata"))
                .thenThrow(new BusinessRuleException("Lo slot è già occupato da un'altra partita"));

        asAdmin(jsonPut("/api/admin/partite/7/stato", "{ \"stato\": \"creata\" }"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errore").value("Lo slot è già occupato da un'altra partita"));
    }

    @Test
    void annulla200SenzaBody() throws Exception {
        when(adminService.annulla(7)).thenReturn(dettaglio("annullata"));

        asAdmin(post("/api/admin/partite/7/annulla"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato").value("annullata"));
    }

    @Test
    void annullaGiaAnnullata409() throws Exception {
        when(adminService.annulla(7)).thenThrow(new BusinessRuleException("La partita è già annullata"));

        asAdmin(post("/api/admin/partite/7/annulla"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errore").value("La partita è già annullata"));
    }

    @Test
    void idNonNumerico400() throws Exception {
        asAdmin(post("/api/admin/partite/abc/annulla"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("Valore non valido per il parametro id"));
    }

    // ============================================
    // Helper
    // ============================================

    private ResultActions asAdmin(MockHttpServletRequestBuilder richiesta) throws Exception {
        return mockMvc.perform(richiesta.with(user(principal(TEL_ADMIN, true))));
    }

    private static MockHttpServletRequestBuilder jsonPut(String path, String body) {
        return put(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static PartitaDTO.DettaglioResponse dettaglio(String stato) {
        return new PartitaDTO.DettaglioResponse(7, 1, "Campo", LocalDate.of(2026, 11, 5), LocalTime.of(18, 0),
                LocalTime.of(19, 30), stato, "PUBBLICA", TEL_UTENTE, List.of(),
                new PartitaDTO.AzioniResponse(false, false, false, false, false), null);
    }

    private static UtentePrincipal principal(String telefono, boolean admin) {
        return new UtentePrincipal(new Utente(telefono, "Mario", "Rossi", "x", LocalDate.of(1990, 1, 1), admin));
    }
}
