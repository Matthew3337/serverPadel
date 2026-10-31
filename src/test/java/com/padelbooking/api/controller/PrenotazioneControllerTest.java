package com.padelbooking.api.controller;

import com.padelbooking.api.config.SecurityConfig;
import com.padelbooking.api.dto.PrenotazioneDTO;
import com.padelbooking.api.exception.BusinessRuleException;
import com.padelbooking.api.exception.ResourceNotFoundException;
import com.padelbooking.api.model.Utente;
import com.padelbooking.api.security.JwtUtil;
import com.padelbooking.api.security.UtenteDetailsService;
import com.padelbooking.api.security.UtentePrincipal;
import com.padelbooking.api.service.PrenotazioneService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PrenotazioneController.class)
@Import(SecurityConfig.class)
class PrenotazioneControllerTest {

    private static final String TEL_1 = "3330000001";
    private static final String TEL_2 = "3330000002";
    private static final String MESSAGGIO_SLOT_OCCUPATO = "Lo slot selezionato è già stato prenotato";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PrenotazioneService prenotazioneService;

    // Dipendenze di SecurityConfig e JwtAuthFilter: senza header Authorization il filtro non le usa
    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UtenteDetailsService utenteDetailsService;

    // ============================================
    // POST /api/prenotazioni
    // ============================================
    @Test
    void postCreaPartitaE201ConResponse() throws Exception {
        LocalDate data = LocalDate.now().plusDays(3);
        when(prenotazioneService.create(any(), anyString())).thenReturn(new PrenotazioneDTO.Response(
                42, 1, "Campo Centrale", TEL_1, TEL_2, null, null,
                data, LocalTime.of(10, 0), LocalTime.of(11, 30), "creata", "PUBBLICA"));

        postPrenotazione(body(data, "10:00", "PUBBLICA"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.idCampo").value(1))
                .andExpect(jsonPath("$.nomeCampo").value("Campo Centrale"))
                .andExpect(jsonPath("$.telefonoGiocatore1").value(TEL_1))
                .andExpect(jsonPath("$.telefonoGiocatore2").value(TEL_2))
                .andExpect(jsonPath("$.telefonoGiocatore3").value(nullValue()))
                .andExpect(jsonPath("$.telefonoGiocatore4").value(nullValue()))
                .andExpect(jsonPath("$.dataPrenotazione").value(data.toString()))
                .andExpect(jsonPath("$.oraInizio").value("10:00:00"))
                .andExpect(jsonPath("$.oraFine").value("11:30:00"))
                .andExpect(jsonPath("$.stato").value("creata"))
                .andExpect(jsonPath("$.tipo").value("PUBBLICA"));
    }

    @Test
    void postPassaAlServiceIlTelefonoDellUtenteAutenticato() throws Exception {
        when(prenotazioneService.create(any(), anyString())).thenReturn(rispostaQualsiasi());

        postPrenotazione(body(LocalDate.now().plusDays(3), "10:00", "PRIVATA")).andExpect(status().isCreated());

        verify(prenotazioneService).create(argThat(r -> "PRIVATA".equals(r.getTipo())), eq(TEL_1));
    }

    @Test
    void postIgnoraITelefoniDeiGiocatoriNelBody() throws Exception {
        // Un client vecchio che indica un altro utente come giocatore 1 non può creare partite a suo nome
        when(prenotazioneService.create(any(), anyString())).thenReturn(rispostaQualsiasi());
        String json = """
                {"idCampo":1,"telefonoGiocatore1":"%s","telefonoGiocatore2":"%s","dataPrenotazione":"%s","oraInizio":"10:00","tipo":"PRIVATA"}
                """.formatted(TEL_2, TEL_2, LocalDate.now().plusDays(3));

        postPrenotazione(json).andExpect(status().isCreated());

        verify(prenotazioneService).create(any(), eq(TEL_1));
    }

    @Test
    void postSenzaTipoE400() throws Exception {
        assertErrore(postPrenotazione(body(LocalDate.now().plusDays(3), "10:00", null)),
                400, "tipo: Il tipo è obbligatorio");
        verify(prenotazioneService, never()).create(any(), anyString());
    }

    @Test
    void postSenzaIdCampoE400() throws Exception {
        String json = """
                {"dataPrenotazione":"%s","oraInizio":"10:00","tipo":"PRIVATA"}
                """.formatted(LocalDate.now().plusDays(3));
        assertErrore(postPrenotazione(json), 400, "idCampo: Il campo è obbligatorio");
        verify(prenotazioneService, never()).create(any(), anyString());
    }

    @Test
    void postSenzaDataE400() throws Exception {
        String json = """
                {"idCampo":1,"oraInizio":"10:00","tipo":"PRIVATA"}
                """;
        assertErrore(postPrenotazione(json), 400, "dataPrenotazione: La data è obbligatoria");
        verify(prenotazioneService, never()).create(any(), anyString());
    }

    @Test
    void postSenzaOraInizioE400() throws Exception {
        String json = """
                {"idCampo":1,"dataPrenotazione":"%s","tipo":"PRIVATA"}
                """.formatted(LocalDate.now().plusDays(3));
        assertErrore(postPrenotazione(json), 400, "oraInizio: L'ora di inizio è obbligatoria");
        verify(prenotazioneService, never()).create(any(), anyString());
    }

    @Test
    void postConTipoPubblicaE201() throws Exception {
        when(prenotazioneService.create(any(), anyString())).thenReturn(rispostaQualsiasi());

        postPrenotazione(body(LocalDate.now().plusDays(3), "10:00", "PUBBLICA")).andExpect(status().isCreated());

        verify(prenotazioneService).create(argThat(r -> "PUBBLICA".equals(r.getTipo())), eq(TEL_1));
    }

    @Test
    void postConTipoNonValidoE400() throws Exception {
        assertErrore(postPrenotazione(body(LocalDate.now().plusDays(3), "10:00", "AMICHEVOLE")),
                400, "tipo: Il tipo deve essere PUBBLICA o PRIVATA");
        verify(prenotazioneService, never()).create(any(), anyString());
    }

    @Test
    void postConTipoMinuscoloE400() throws Exception {
        assertErrore(postPrenotazione(body(LocalDate.now().plusDays(3), "10:00", "pubblica")),
                400, "tipo: Il tipo deve essere PUBBLICA o PRIVATA");
        verify(prenotazioneService, never()).create(any(), anyString());
    }

    @Test
    void postSlotOccupatoE409() throws Exception {
        when(prenotazioneService.create(any(), anyString())).thenThrow(new BusinessRuleException(MESSAGGIO_SLOT_OCCUPATO));

        assertErrore(postPrenotazione(body(LocalDate.now().plusDays(3), "10:00", "PRIVATA")), 409, MESSAGGIO_SLOT_OCCUPATO);
    }

    @Test
    void postFuoriOrarioE409() throws Exception {
        String msg = "L'orario richiesto è fuori dalla fascia di apertura del campo";
        when(prenotazioneService.create(any(), anyString())).thenThrow(new BusinessRuleException(msg));

        assertErrore(postPrenotazione(body(LocalDate.now().plusDays(3), "23:00", "PRIVATA")), 409, msg);
    }

    @Test
    void postSlotNelPassatoE409() throws Exception {
        String msg = "Non è possibile prenotare uno slot nel passato";
        when(prenotazioneService.create(any(), anyString())).thenThrow(new BusinessRuleException(msg));

        assertErrore(postPrenotazione(body(LocalDate.now().minusDays(1), "10:00", "PRIVATA")), 409, msg);
    }

    @Test
    void postCampoInesistenteE404() throws Exception {
        when(prenotazioneService.create(any(), anyString())).thenThrow(new ResourceNotFoundException("Campo non trovato con id 1"));

        assertErrore(postPrenotazione(body(LocalDate.now().plusDays(3), "10:00", "PRIVATA")), 404, "Campo non trovato con id 1");
    }

    @Test
    void postUtenteInesistenteE404() throws Exception {
        // L'utente del token non esiste più nel database
        String msg = "Utente non trovato con telefono " + TEL_1;
        when(prenotazioneService.create(any(), anyString())).thenThrow(new ResourceNotFoundException(msg));

        assertErrore(postPrenotazione(body(LocalDate.now().plusDays(3), "10:00", "PRIVATA")), 404, msg);
    }

    @Test
    void postSenzaAutenticazioneVieneRifiutato() throws Exception {
        mockMvc.perform(post("/api/prenotazioni")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(LocalDate.now().plusDays(3), "10:00", "PRIVATA")))
                .andExpect(status().isForbidden());
        verify(prenotazioneService, never()).create(any(), anyString());
    }

    // ============================================
    // DELETE /api/prenotazioni/{id}
    // ============================================
    @Test
    void deleteDelCreatoreE204ConFlagAdminFalse() throws Exception {
        mockMvc.perform(delete("/api/prenotazioni/5").with(user(principal(TEL_1, false))))
                .andExpect(status().isNoContent());

        verify(prenotazioneService).cancel(5, TEL_1, false);
    }

    @Test
    void deleteDellAdminPassaFlagAdminTrue() throws Exception {
        mockMvc.perform(delete("/api/prenotazioni/5").with(user(principal(TEL_2, true))))
                .andExpect(status().isNoContent());

        verify(prenotazioneService).cancel(5, TEL_2, true);
    }

    @Test
    void deletePartitaInesistenteE404() throws Exception {
        doThrow(new ResourceNotFoundException("Prenotazione non trovata con id 5"))
                .when(prenotazioneService).cancel(5, TEL_1, false);

        assertErrore(mockMvc.perform(delete("/api/prenotazioni/5").with(user(principal(TEL_1, false)))),
                404, "Prenotazione non trovata con id 5");
    }

    @Test
    void deleteSenzaPermessiE409() throws Exception {
        String msg = "Non hai i permessi per cancellare questa prenotazione";
        doThrow(new BusinessRuleException(msg)).when(prenotazioneService).cancel(5, TEL_2, false);

        assertErrore(mockMvc.perform(delete("/api/prenotazioni/5").with(user(principal(TEL_2, false)))), 409, msg);
    }

    @Test
    void deleteSotto24OreE409() throws Exception {
        String msg = "Non è possibile cancellare una prenotazione a meno di 24 ore dall'inizio";
        doThrow(new BusinessRuleException(msg)).when(prenotazioneService).cancel(5, TEL_1, false);

        assertErrore(mockMvc.perform(delete("/api/prenotazioni/5").with(user(principal(TEL_1, false)))), 409, msg);
    }

    @Test
    void deleteGiaGiocataE409() throws Exception {
        String msg = "Non è possibile annullare una partita già giocata";
        doThrow(new BusinessRuleException(msg)).when(prenotazioneService).cancel(5, TEL_2, true);

        assertErrore(mockMvc.perform(delete("/api/prenotazioni/5").with(user(principal(TEL_2, true)))), 409, msg);
    }

    @Test
    void deleteSenzaAutenticazioneVieneRifiutato() throws Exception {
        mockMvc.perform(delete("/api/prenotazioni/5")).andExpect(status().isForbidden());
        verify(prenotazioneService, never()).cancel(any(), anyString(), anyBoolean());
    }

    // ============================================
    // GET slot-disponibili, prossima, utente/{telefono}
    // ============================================
    @Test
    void slotDisponibiliE200() throws Exception {
        LocalDate data = LocalDate.of(2030, 7, 10);
        when(prenotazioneService.getSlotDisponibili(1, data)).thenReturn(List.of(
                new PrenotazioneDTO.SlotResponse(1, "Campo Centrale", LocalTime.of(8, 0), LocalTime.of(9, 30), true),
                new PrenotazioneDTO.SlotResponse(1, "Campo Centrale", LocalTime.of(9, 30), LocalTime.of(11, 0), false)));

        mockMvc.perform(get("/api/prenotazioni/slot-disponibili").param("idCampo", "1").param("data", "2030-07-10")
                        .with(user(principal(TEL_1, false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].idCampo").value(1))
                .andExpect(jsonPath("$[0].nomeCampo").value("Campo Centrale"))
                .andExpect(jsonPath("$[0].oraInizio").value("08:00:00"))
                .andExpect(jsonPath("$[0].disponibile").value(true))
                .andExpect(jsonPath("$[1].disponibile").value(false));
    }

    @Test
    void prossimaUtenteInesistenteE404() throws Exception {
        String msg = "Utente non trovato con telefono 3339999999";
        when(prenotazioneService.getProssimaByTelefono("3339999999")).thenThrow(new ResourceNotFoundException(msg));

        assertErrore(mockMvc.perform(get("/api/prenotazioni/prossima").param("telefono", "3339999999")
                .with(user(principal(TEL_1, false)))), 404, msg);
    }

    @Test
    void prossimaAssenteE404() throws Exception {
        String msg = "Nessuna prenotazione futura trovata per il telefono " + TEL_1;
        when(prenotazioneService.getProssimaByTelefono(TEL_1)).thenThrow(new ResourceNotFoundException(msg));

        assertErrore(mockMvc.perform(get("/api/prenotazioni/prossima").param("telefono", TEL_1)
                .with(user(principal(TEL_1, false)))), 404, msg);
    }

    @Test
    void prossimaE200() throws Exception {
        when(prenotazioneService.getProssimaByTelefono(TEL_1)).thenReturn(rispostaQualsiasi());

        mockMvc.perform(get("/api/prenotazioni/prossima").param("telefono", TEL_1).with(user(principal(TEL_1, false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.stato").value("creata"))
                .andExpect(jsonPath("$.tipo").value("PRIVATA"));
    }

    @Test
    void storicoUtenteInesistenteE404() throws Exception {
        String msg = "Utente non trovato con telefono 3339999999";
        when(prenotazioneService.getByUtente("3339999999")).thenThrow(new ResourceNotFoundException(msg));

        assertErrore(mockMvc.perform(get("/api/prenotazioni/utente/3339999999")
                .with(user(principal(TEL_1, false)))), 404, msg);
    }

    @Test
    void storicoE200() throws Exception {
        when(prenotazioneService.getByUtente(TEL_1)).thenReturn(List.of(rispostaQualsiasi()));

        mockMvc.perform(get("/api/prenotazioni/utente/" + TEL_1).with(user(principal(TEL_1, false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].telefonoGiocatore1").value(TEL_1));
    }

    // ============================================
    // Errori 400 di parsing e parametri
    // ============================================
    @Test
    void postJsonTroncatoE400() throws Exception {
        assertErrore(postPrenotazione("{\"idCampo\":1,\"tipo\":\"PRI"),
                400, "Corpo della richiesta non valido: controlla che sia un JSON corretto");
        verify(prenotazioneService, never()).create(any(), anyString());
    }

    @Test
    void postJsonTroncatoTraDueCampiE400() throws Exception {
        assertErrore(postPrenotazione("{\"idCampo\":1,"),
                400, "Corpo della richiesta non valido: controlla che sia un JSON corretto");
        verify(prenotazioneService, never()).create(any(), anyString());
    }

    @Test
    void postDataInFormatoErratoE400() throws Exception {
        String json = """
                {"idCampo":1,"dataPrenotazione":"31/10/2026","oraInizio":"10:00","tipo":"PRIVATA"}
                """;
        assertErrore(postPrenotazione(json), 400,
                "Valore non valido per il campo dataPrenotazione (date nel formato AAAA-MM-GG, orari nel formato HH:MM o HH:MM:SS)");
        verify(prenotazioneService, never()).create(any(), anyString());
    }

    @Test
    void prossimaSenzaTelefonoE400() throws Exception {
        assertErrore(mockMvc.perform(get("/api/prenotazioni/prossima").with(user(principal(TEL_1, false)))),
                400, "Parametro obbligatorio mancante: telefono");
        verify(prenotazioneService, never()).getProssimaByTelefono(anyString());
    }

    @Test
    void slotDisponibiliConIdCampoNonNumericoE400() throws Exception {
        assertErrore(mockMvc.perform(get("/api/prenotazioni/slot-disponibili").param("idCampo", "abc")
                        .param("data", "2030-07-10").with(user(principal(TEL_1, false)))),
                400, "Valore non valido per il parametro idCampo");
        verify(prenotazioneService, never()).getSlotDisponibili(any(), any());
    }

    @Test
    void deleteConIdNonNumericoE400() throws Exception {
        assertErrore(mockMvc.perform(delete("/api/prenotazioni/abc").with(user(principal(TEL_1, false)))),
                400, "Valore non valido per il parametro id");
        verify(prenotazioneService, never()).cancel(any(), anyString(), anyBoolean());
    }

    // ============================================
    // Helper
    // ============================================
    private ResultActions postPrenotazione(String json) throws Exception {
        return mockMvc.perform(post("/api/prenotazioni")
                .with(user(principal(TEL_1, false)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    // Formato d'errore su cui si basa il client Flutter: { timestamp, status, errore }
    private static void assertErrore(ResultActions azione, int status, String errore) throws Exception {
        azione.andExpect(status().is(status))
                .andExpect(jsonPath("$.timestamp").value(notNullValue()))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.errore").value(errore))
                .andExpect(jsonPath("$.length()").value(3));
    }

    private static String body(LocalDate data, String oraInizio, String tipo) {
        String campoTipo = tipo == null ? "" : ",\"tipo\":\"" + tipo + "\"";
        return """
                {"idCampo":1,"dataPrenotazione":"%s","oraInizio":"%s"%s}
                """.formatted(data, oraInizio, campoTipo);
    }

    private static UtentePrincipal principal(String telefono, boolean admin) {
        return new UtentePrincipal(new Utente(telefono, "Nome", "Cognome", "hash", LocalDate.of(1990, 1, 1), admin));
    }

    private static PrenotazioneDTO.Response rispostaQualsiasi() {
        return new PrenotazioneDTO.Response(1, 1, "Campo Centrale", TEL_1, null, null, null,
                LocalDate.now().plusDays(3), LocalTime.of(10, 0), LocalTime.of(11, 30), "creata", "PRIVATA");
    }
}
