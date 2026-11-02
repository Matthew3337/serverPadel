package com.padelbooking.api.controller;

import com.padelbooking.api.config.SecurityConfig;
import com.padelbooking.api.dto.PartitaDTO;
import com.padelbooking.api.exception.AccessoNegatoException;
import com.padelbooking.api.exception.BusinessRuleException;
import com.padelbooking.api.exception.ResourceNotFoundException;
import com.padelbooking.api.exception.RichiestaNonValidaException;
import com.padelbooking.api.model.Utente;
import com.padelbooking.api.security.JwtUtil;
import com.padelbooking.api.security.UtenteDetailsService;
import com.padelbooking.api.security.UtentePrincipal;
import com.padelbooking.api.service.PartitaService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
                List.of(new PartitaDTO.PostoResponse(11, "UTENTE", TEL_1, "Mario", "Rossi", 2.5, false, null),
                        new PartitaDTO.PostoResponse(12, "CAMPO", null, null, null, null, false, null)),
                new PartitaDTO.AzioniResponse(true, true, false, false, false), null);
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
                .andExpect(jsonPath("$.posti[0].idPosto").value(11))
                .andExpect(jsonPath("$.posti[0].squadra").value(nullValue()))
                .andExpect(jsonPath("$.risultato").value(nullValue()))
                .andExpect(jsonPath("$.posti[1].idPosto").value(12))
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
    // Storico
    // ============================================

    @Test
    void getStorico200ConFormatoDelContrattoENonPassaDalDettaglio() throws Exception {
        PartitaDTO.RisultatoResponse risultato = new PartitaDTO.RisultatoResponse(
                List.of(new PartitaDTO.SetResponse(6, 4), new PartitaDTO.SetResponse(3, 6),
                        new PartitaDTO.SetResponse(6, 2)),
                1, "VINTA", 12);
        PartitaDTO.StoricoResponse giocata = new PartitaDTO.StoricoResponse(
                7, 1, "Campo 1", LocalDate.of(2026, 11, 5), LocalTime.of(18, 0), LocalTime.of(19, 30),
                "giocata", "PUBBLICA", risultato);
        PartitaDTO.StoricoResponse creata = new PartitaDTO.StoricoResponse(
                8, 1, "Campo 1", LocalDate.of(2026, 11, 1), LocalTime.of(18, 0), LocalTime.of(19, 30),
                "creata", "PRIVATA", null);
        when(partitaService.getStorico(TEL_1, false)).thenReturn(List.of(giocata, creata));

        mockMvc.perform(get("/api/partite/storico").with(user(principal(TEL_1, false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].idCampo").value(1))
                .andExpect(jsonPath("$[0].nomeCampo").value("Campo 1"))
                .andExpect(jsonPath("$[0].dataPartita").value("2026-11-05"))
                .andExpect(jsonPath("$[0].oraInizio").value("18:00:00"))
                .andExpect(jsonPath("$[0].oraFine").value("19:30:00"))
                .andExpect(jsonPath("$[0].stato").value("giocata"))
                .andExpect(jsonPath("$[0].tipo").value("PUBBLICA"))
                .andExpect(jsonPath("$[0].length()").value(9))
                .andExpect(jsonPath("$[0].risultato.set.length()").value(3))
                .andExpect(jsonPath("$[0].risultato.set[0].squadra1").value(6))
                .andExpect(jsonPath("$[0].risultato.set[0].squadra2").value(4))
                .andExpect(jsonPath("$[0].risultato.set[2].squadra1").value(6))
                .andExpect(jsonPath("$[0].risultato.set[2].squadra2").value(2))
                .andExpect(jsonPath("$[0].risultato.squadraUtente").value(1))
                .andExpect(jsonPath("$[0].risultato.esito").value("VINTA"))
                .andExpect(jsonPath("$[0].risultato.puntiAssegnati").value(12))
                .andExpect(jsonPath("$[0].risultato.length()").value(4))
                // risultato null va serializzato come null, non omesso
                .andExpect(jsonPath("$[1].risultato").value(nullValue()))
                .andExpect(jsonPath("$[1].length()").value(9));

        verify(partitaService, never()).getDettaglio(anyInt(), anyString(), anyBoolean());
    }

    @Test
    void getStoricoCampiNullDelRisultatoSerializzatiComeNull() throws Exception {
        PartitaDTO.RisultatoResponse risultato = new PartitaDTO.RisultatoResponse(
                List.of(new PartitaDTO.SetResponse(6, 4), new PartitaDTO.SetResponse(6, 3)), null, null, null);
        when(partitaService.getStorico(TEL_1, false)).thenReturn(List.of(new PartitaDTO.StoricoResponse(
                7, 1, "Campo 1", LocalDate.of(2026, 11, 5), LocalTime.of(18, 0), LocalTime.of(19, 30),
                "giocata", "PUBBLICA", risultato)));

        mockMvc.perform(get("/api/partite/storico").with(user(principal(TEL_1, false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].risultato.set.length()").value(2))
                .andExpect(jsonPath("$[0].risultato.length()").value(4))
                .andExpect(jsonPath("$[0].risultato.squadraUtente").value(nullValue()))
                .andExpect(jsonPath("$[0].risultato.esito").value(nullValue()))
                .andExpect(jsonPath("$[0].risultato.puntiAssegnati").value(nullValue()));
    }

    @Test
    void getStoricoVuotoPerAdmin() throws Exception {
        when(partitaService.getStorico(TEL_1, true)).thenReturn(List.of());

        mockMvc.perform(get("/api/partite/storico").with(user(principal(TEL_1, true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getStoricoSenzaAutenticazioneRifiutato() throws Exception {
        mockMvc.perform(get("/api/partite/storico")).andExpect(status().isForbidden());

        verify(partitaService, never()).getStorico(anyString(), anyBoolean());
    }

    // ============================================
    // Ingresso
    // ============================================

    @Test
    void entra200ConDettaglioAggiornato() throws Exception {
        PartitaDTO.DettaglioResponse r = new PartitaDTO.DettaglioResponse(
                7, 1, "Campo 1", LocalDate.of(2026, 11, 5), LocalTime.of(18, 0), LocalTime.of(19, 30),
                "creata", "PUBBLICA", "3330000009",
                List.of(new PartitaDTO.PostoResponse(13, "UTENTE", "3330000009", "Anna", "Bianchi", 2.0, false, null),
                        new PartitaDTO.PostoResponse(14, "UTENTE", TEL_1, "Nome", "Cognome", 1.0, false, null)),
                new PartitaDTO.AzioniResponse(false, false, true, false, false), null);
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

    // ============================================
    // Eliminazione
    // ============================================

    @Test
    void elimina204SenzaBody() throws Exception {
        mockMvc.perform(delete("/api/partite/7").with(user(principal(TEL_1, false))))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(partitaService).elimina(7, TEL_1, false);
    }

    @Test
    void elimina409ConFormatoErrore() throws Exception {
        String messaggio = "L'eliminazione è chiusa: mancano meno di 24 ore all'inizio. "
                + "Solo l'amministratore può annullare la partita";
        doThrow(new BusinessRuleException(messaggio)).when(partitaService).elimina(7, TEL_1, false);

        mockMvc.perform(delete("/api/partite/7").with(user(principal(TEL_1, false))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.timestamp").value(notNullValue()))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errore").value(messaggio))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void elimina403PerNonCreatore() throws Exception {
        doThrow(new AccessoNegatoException("Solo il creatore può eliminare la partita"))
                .when(partitaService).elimina(7, TEL_1, false);

        mockMvc.perform(delete("/api/partite/7").with(user(principal(TEL_1, false))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errore").value("Solo il creatore può eliminare la partita"));
    }

    @Test
    void elimina403PerAdminPassaIsAdminAlService() throws Exception {
        doThrow(new AccessoNegatoException("L'amministratore non può eliminare le partite da qui"))
                .when(partitaService).elimina(7, TEL_1, true);

        mockMvc.perform(delete("/api/partite/7").with(user(principal(TEL_1, true))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errore").value("L'amministratore non può eliminare le partite da qui"));
    }

    @Test
    void elimina404ConFormatoErrore() throws Exception {
        doThrow(new ResourceNotFoundException("Partita non trovata con id 7"))
                .when(partitaService).elimina(7, TEL_1, false);

        mockMvc.perform(delete("/api/partite/7").with(user(principal(TEL_1, false))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.errore").value("Partita non trovata con id 7"));
    }

    @Test
    void eliminaSenzaAutenticazioneRifiutato() throws Exception {
        mockMvc.perform(delete("/api/partite/7")).andExpect(status().isForbidden());

        verify(partitaService, never()).elimina(anyInt(), anyString(), anyBoolean());
    }

    // ============================================
    // Uscita
    // ============================================

    @Test
    void esci204SenzaBody() throws Exception {
        mockMvc.perform(post("/api/partite/7/esci").with(user(principal(TEL_1, false))))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(partitaService).esci(7, TEL_1, false);
    }

    @Test
    void esci409ConFormatoErrore() throws Exception {
        doThrow(new BusinessRuleException("Non partecipi a questa partita"))
                .when(partitaService).esci(7, TEL_1, false);

        mockMvc.perform(post("/api/partite/7/esci").with(user(principal(TEL_1, false))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.timestamp").value(notNullValue()))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errore").value("Non partecipi a questa partita"))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void esci403PerAdminPassaIsAdminAlService() throws Exception {
        doThrow(new AccessoNegatoException("L'amministratore non può uscire dalle partite"))
                .when(partitaService).esci(7, TEL_1, true);

        mockMvc.perform(post("/api/partite/7/esci").with(user(principal(TEL_1, true))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errore").value("L'amministratore non può uscire dalle partite"));
    }

    @Test
    void esci404ConFormatoErrore() throws Exception {
        doThrow(new ResourceNotFoundException("Partita non trovata con id 7"))
                .when(partitaService).esci(7, TEL_1, false);

        mockMvc.perform(post("/api/partite/7/esci").with(user(principal(TEL_1, false))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errore").value("Partita non trovata con id 7"));
    }

    @Test
    void esciSenzaAutenticazioneRifiutato() throws Exception {
        mockMvc.perform(post("/api/partite/7/esci")).andExpect(status().isForbidden());

        verify(partitaService, never()).esci(anyInt(), anyString(), anyBoolean());
    }

    // ============================================
    // Conferma
    // ============================================

    @Test
    void conferma200ConDettaglioAggiornato() throws Exception {
        PartitaDTO.DettaglioResponse r = new PartitaDTO.DettaglioResponse(
                7, 1, "Campo 1", LocalDate.of(2026, 11, 5), LocalTime.of(18, 0), LocalTime.of(19, 30),
                "confermata", "PRIVATA", TEL_1,
                List.of(new PartitaDTO.PostoResponse(15, "UTENTE", TEL_1, "Mario", "Rossi", 2.5, true, null),
                        new PartitaDTO.PostoResponse(16, "CAMPO", null, null, null, null, false, null)),
                new PartitaDTO.AzioniResponse(false, false, false, false, false), null);
        when(partitaService.conferma(7, TEL_1, false)).thenReturn(r);

        mockMvc.perform(post("/api/partite/7/conferma").with(user(principal(TEL_1, false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.stato").value("confermata"))
                .andExpect(jsonPath("$.posti[0].confermata").value(true))
                .andExpect(jsonPath("$.posti[1].confermata").value(false))
                .andExpect(jsonPath("$.azioni.puoConfermare").value(false));
    }

    @Test
    void conferma409ConFormatoErrore() throws Exception {
        String messaggio = "La conferma è possibile solo tra 24 e 12 ore prima dell'inizio";
        when(partitaService.conferma(7, TEL_1, false)).thenThrow(new BusinessRuleException(messaggio));

        mockMvc.perform(post("/api/partite/7/conferma").with(user(principal(TEL_1, false))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.timestamp").value(notNullValue()))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errore").value(messaggio))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void conferma403PerAdminPassaIsAdminAlService() throws Exception {
        when(partitaService.conferma(7, TEL_1, true))
                .thenThrow(new AccessoNegatoException("L'amministratore non conferma le partite"));

        mockMvc.perform(post("/api/partite/7/conferma").with(user(principal(TEL_1, true))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errore").value("L'amministratore non conferma le partite"));
    }

    @Test
    void conferma404ConFormatoErrore() throws Exception {
        when(partitaService.conferma(7, TEL_1, false))
                .thenThrow(new ResourceNotFoundException("Partita non trovata con id 7"));

        mockMvc.perform(post("/api/partite/7/conferma").with(user(principal(TEL_1, false))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.errore").value("Partita non trovata con id 7"));
    }

    @Test
    void confermaSenzaAutenticazioneRifiutata() throws Exception {
        mockMvc.perform(post("/api/partite/7/conferma")).andExpect(status().isForbidden());

        verify(partitaService, never()).conferma(anyInt(), anyString(), anyBoolean());
    }

    // ============================================
    // Utenti invitabili
    // ============================================

    @Test
    void getInvitabili200ConFormatoDelContrattoEFiltroPassatoAlService() throws Exception {
        when(partitaService.getInvitabili(7, "ros", TEL_1, false)).thenReturn(List.of(
                new PartitaDTO.InvitabileResponse("3330000002", "Luigi", "Rossi", 3.5)));

        mockMvc.perform(get("/api/partite/7/invitabili").param("cerca", "ros").with(user(principal(TEL_1, false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].telefono").value("3330000002"))
                .andExpect(jsonPath("$[0].nome").value("Luigi"))
                .andExpect(jsonPath("$[0].cognome").value("Rossi"))
                .andExpect(jsonPath("$[0].livello").value(3.5))
                .andExpect(jsonPath("$[0].length()").value(4));

        verify(partitaService, never()).getDettaglio(anyInt(), anyString(), anyBoolean());
    }

    @Test
    void getInvitabiliSenzaCercaPassaNull() throws Exception {
        when(partitaService.getInvitabili(eq(7), isNull(), eq(TEL_1), eq(false))).thenReturn(List.of());

        mockMvc.perform(get("/api/partite/7/invitabili").with(user(principal(TEL_1, false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getInvitabili403ConFormatoErrore() throws Exception {
        when(partitaService.getInvitabili(7, null, TEL_1, true))
                .thenThrow(new AccessoNegatoException("Solo il creatore può invitare"));

        mockMvc.perform(get("/api/partite/7/invitabili").with(user(principal(TEL_1, true))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.timestamp").value(notNullValue()))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errore").value("Solo il creatore può invitare"))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void getInvitabili404ConFormatoErrore() throws Exception {
        when(partitaService.getInvitabili(7, null, TEL_1, false))
                .thenThrow(new ResourceNotFoundException("Partita non trovata con id 7"));

        mockMvc.perform(get("/api/partite/7/invitabili").with(user(principal(TEL_1, false))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.errore").value("Partita non trovata con id 7"));
    }

    @Test
    void getInvitabiliSenzaAutenticazioneRifiutato() throws Exception {
        mockMvc.perform(get("/api/partite/7/invitabili")).andExpect(status().isForbidden());

        verify(partitaService, never()).getInvitabili(anyInt(), any(), anyString(), anyBoolean());
    }

    // ============================================
    // Invito
    // ============================================

    @Test
    void invita200ConDettaglioAggiornato() throws Exception {
        PartitaDTO.DettaglioResponse r = new PartitaDTO.DettaglioResponse(
                7, 1, "Campo 1", LocalDate.of(2026, 11, 5), LocalTime.of(18, 0), LocalTime.of(19, 30),
                "creata", "PRIVATA", TEL_1,
                List.of(new PartitaDTO.PostoResponse(17, "UTENTE", TEL_1, "Nome", "Cognome", 1.0, false, null),
                        new PartitaDTO.PostoResponse(18, "UTENTE", "3330000002", "Luigi", "Rossi", 3.5, false, null)),
                new PartitaDTO.AzioniResponse(true, true, false, false, false), null);
        when(partitaService.invita(7, "3330000002", TEL_1, false)).thenReturn(r);

        mockMvc.perform(post("/api/partite/7/inviti").with(user(principal(TEL_1, false)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefono\": \"3330000002\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.tipo").value("PRIVATA"))
                .andExpect(jsonPath("$.posti.length()").value(2))
                .andExpect(jsonPath("$.posti[1].telefono").value("3330000002"))
                .andExpect(jsonPath("$.azioni.puoInvitare").value(true));
    }

    @Test
    void invitaSenzaTelefono400() throws Exception {
        mockMvc.perform(post("/api/partite/7/inviti").with(user(principal(TEL_1, false)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errore").value("telefono: Il telefono è obbligatorio"))
                .andExpect(jsonPath("$.length()").value(3));

        verify(partitaService, never()).invita(anyInt(), any(), anyString(), anyBoolean());
    }

    @Test
    void invitaTelefonoVuoto400() throws Exception {
        mockMvc.perform(post("/api/partite/7/inviti").with(user(principal(TEL_1, false)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefono\": \"  \"}"))
                .andExpect(status().isBadRequest());

        verify(partitaService, never()).invita(anyInt(), any(), anyString(), anyBoolean());
    }

    @Test
    void invita409ConMessaggioDellaRegola() throws Exception {
        when(partitaService.invita(7, "3330000002", TEL_1, false))
                .thenThrow(new BusinessRuleException("Gli inviti sono chiusi: mancano meno di 24 ore all'inizio"));

        mockMvc.perform(post("/api/partite/7/inviti").with(user(principal(TEL_1, false)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefono\": \"3330000002\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.timestamp").value(notNullValue()))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errore").value("Gli inviti sono chiusi: mancano meno di 24 ore all'inizio"))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void invita404UtenteNonTrovato() throws Exception {
        when(partitaService.invita(7, "3330000002", TEL_1, false))
                .thenThrow(new ResourceNotFoundException("Utente non trovato"));

        mockMvc.perform(post("/api/partite/7/inviti").with(user(principal(TEL_1, false)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefono\": \"3330000002\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.errore").value("Utente non trovato"));
    }

    @Test
    void invita403PerAdminPassaIsAdminAlService() throws Exception {
        when(partitaService.invita(7, "3330000002", TEL_1, true))
                .thenThrow(new AccessoNegatoException("Solo il creatore può invitare"));

        mockMvc.perform(post("/api/partite/7/inviti").with(user(principal(TEL_1, true)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefono\": \"3330000002\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errore").value("Solo il creatore può invitare"));
    }

    @Test
    void invitaSenzaAutenticazioneRifiutato() throws Exception {
        mockMvc.perform(post("/api/partite/7/inviti")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefono\": \"3330000002\"}"))
                .andExpect(status().isForbidden());

        verify(partitaService, never()).invita(anyInt(), any(), anyString(), anyBoolean());
    }

    // ============================================
    // Bacheca inviti
    // ============================================

    @Test
    void getBachecaInviti200ConFormatoDelContrattoENonPassaDalDettaglio() throws Exception {
        PartitaDTO.InvitoRicevutoResponse riga = new PartitaDTO.InvitoRicevutoResponse(
                7, 1, "Campo 1", LocalDate.of(2026, 11, 5), LocalTime.of(18, 0), LocalTime.of(19, 30),
                "confermata", "PRIVATA", "Mario", "Rossi");
        when(partitaService.getBachecaInviti(TEL_1, false)).thenReturn(List.of(riga));

        mockMvc.perform(get("/api/partite/inviti").with(user(principal(TEL_1, false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].idCampo").value(1))
                .andExpect(jsonPath("$[0].nomeCampo").value("Campo 1"))
                .andExpect(jsonPath("$[0].dataPartita").value("2026-11-05"))
                .andExpect(jsonPath("$[0].oraInizio").value("18:00:00"))
                .andExpect(jsonPath("$[0].oraFine").value("19:30:00"))
                .andExpect(jsonPath("$[0].stato").value("confermata"))
                .andExpect(jsonPath("$[0].tipo").value("PRIVATA"))
                .andExpect(jsonPath("$[0].nomeCreatore").value("Mario"))
                .andExpect(jsonPath("$[0].cognomeCreatore").value("Rossi"))
                .andExpect(jsonPath("$[0].length()").value(10));

        verify(partitaService, never()).getDettaglio(anyInt(), anyString(), anyBoolean());
    }

    @Test
    void getBachecaInvitiVuotaPerAdmin() throws Exception {
        when(partitaService.getBachecaInviti(TEL_1, true)).thenReturn(List.of());

        mockMvc.perform(get("/api/partite/inviti").with(user(principal(TEL_1, true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getBachecaInvitiSenzaAutenticazioneRifiutata() throws Exception {
        mockMvc.perform(get("/api/partite/inviti")).andExpect(status().isForbidden());

        verify(partitaService, never()).getBachecaInviti(anyString(), anyBoolean());
    }

    // ============================================
    // Registrazione del risultato
    // ============================================

    private static final String BODY_RISULTATO = """
            { "squadra1": [11, 13], "squadra2": [12, 14],
              "set": [ { "squadra1": 6, "squadra2": 4 }, { "squadra1": 3, "squadra2": 6 }, { "squadra1": 6, "squadra2": 2 } ] }
            """;

    @Test
    void registraRisultato200ConDettaglioEBodyPassatoAlService() throws Exception {
        PartitaDTO.RisultatoResponse risultato = new PartitaDTO.RisultatoResponse(
                List.of(new PartitaDTO.SetResponse(6, 4), new PartitaDTO.SetResponse(3, 6), new PartitaDTO.SetResponse(6, 2)),
                1, "VINTA", null);
        PartitaDTO.DettaglioResponse r = new PartitaDTO.DettaglioResponse(
                7, 1, "Campo 1", LocalDate.of(2026, 11, 5), LocalTime.of(18, 0), LocalTime.of(19, 30),
                "giocata", "PRIVATA", TEL_1,
                List.of(new PartitaDTO.PostoResponse(11, "UTENTE", TEL_1, "Mario", "Rossi", 2.5, true, 1),
                        new PartitaDTO.PostoResponse(14, "CAMPO", null, null, null, null, false, 2)),
                new PartitaDTO.AzioniResponse(false, false, false, false, false), risultato);
        ArgumentCaptor<PartitaDTO.RisultatoRequest> body = ArgumentCaptor.forClass(PartitaDTO.RisultatoRequest.class);
        when(partitaService.registraRisultato(eq(7), body.capture(), eq(TEL_1), eq(false))).thenReturn(r);

        mockMvc.perform(post("/api/partite/7/risultato").with(user(principal(TEL_1, false)))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY_RISULTATO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato").value("giocata"))
                .andExpect(jsonPath("$.posti[0].idPosto").value(11))
                .andExpect(jsonPath("$.posti[0].squadra").value(1))
                .andExpect(jsonPath("$.posti[1].idPosto").value(14))
                .andExpect(jsonPath("$.posti[1].squadra").value(2))
                .andExpect(jsonPath("$.azioni.puoRegistrareRisultato").value(false))
                .andExpect(jsonPath("$.risultato.set.length()").value(3))
                .andExpect(jsonPath("$.risultato.set[1].squadra1").value(3))
                .andExpect(jsonPath("$.risultato.set[1].squadra2").value(6))
                .andExpect(jsonPath("$.risultato.squadraUtente").value(1))
                .andExpect(jsonPath("$.risultato.esito").value("VINTA"))
                .andExpect(jsonPath("$.risultato.puntiAssegnati").value(nullValue()))
                .andExpect(jsonPath("$.risultato.length()").value(4));

        assertThat(body.getValue().getSquadra1()).containsExactly(11, 13);
        assertThat(body.getValue().getSquadra2()).containsExactly(12, 14);
        assertThat(body.getValue().getSet()).hasSize(3);
        assertThat(body.getValue().getSet().get(2).getSquadra1()).isEqualTo(6);
        assertThat(body.getValue().getSet().get(2).getSquadra2()).isEqualTo(2);
    }

    @Test
    void registraRisultatoPassaIlFlagAdminAlService() throws Exception {
        when(partitaService.registraRisultato(eq(7), any(), eq(TEL_1), eq(true)))
                .thenThrow(new AccessoNegatoException("L'amministratore modifica i risultati dalla propria area"));

        mockMvc.perform(post("/api/partite/7/risultato").with(user(principal(TEL_1, true)))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY_RISULTATO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errore").value("L'amministratore modifica i risultati dalla propria area"))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void registraRisultatoRichiestaNonValidaDa400ConIlFormatoDegliErrori() throws Exception {
        when(partitaService.registraRisultato(eq(7), any(), eq(TEL_1), eq(false)))
                .thenThrow(new RichiestaNonValidaException("Ogni squadra deve avere 2 giocatori"));

        mockMvc.perform(post("/api/partite/7/risultato").with(user(principal(TEL_1, false)))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY_RISULTATO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                // Il messaggio è quello del contratto, senza il prefisso del campo della validazione
                .andExpect(jsonPath("$.errore").value("Ogni squadra deve avere 2 giocatori"))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void registraRisultatoCampiMancantiArrivanoAlServiceCheLiGiudica() throws Exception {
        // Niente @Valid: un body vuoto non si ferma al controller, così una partita non visibile dà comunque 404
        when(partitaService.registraRisultato(eq(7), any(), eq(TEL_1), eq(false)))
                .thenThrow(new ResourceNotFoundException("Partita non trovata con id 7"));

        mockMvc.perform(post("/api/partite/7/risultato").with(user(principal(TEL_1, false)))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.errore").value("Partita non trovata con id 7"));
    }

    @Test
    void registraRisultatoRegolaViolataDa409() throws Exception {
        when(partitaService.registraRisultato(eq(7), any(), eq(TEL_1), eq(false)))
                .thenThrow(new BusinessRuleException("Il risultato è già stato registrato"));

        mockMvc.perform(post("/api/partite/7/risultato").with(user(principal(TEL_1, false)))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY_RISULTATO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errore").value("Il risultato è già stato registrato"))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void registraRisultatoJsonNonValidoDa400SenzaChiamareIlService() throws Exception {
        mockMvc.perform(post("/api/partite/7/risultato").with(user(principal(TEL_1, false)))
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"squadra1\": [11, "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errore", notNullValue()));

        verify(partitaService, never()).registraRisultato(anyInt(), any(), anyString(), anyBoolean());
    }

    // spring.jackson.deserialization.accept-float-as-int=false: senza, Jackson troncherebbe 6.5 a 6 e
    // registrerebbe un risultato diverso da quello inviato. Vale anche per 6.0: un game è un intero.
    @Test
    void registraRisultatoGameConDecimaliDa400SenzaChiamareIlService() throws Exception {
        for (String game : List.of("6.5", "6.0")) {
            String body = """
                    { "squadra1": [11, 13], "squadra2": [12, 14],
                      "set": [ { "squadra1": %s, "squadra2": 4 }, { "squadra1": 6, "squadra2": 3 } ] }
                    """.formatted(game);

            mockMvc.perform(post("/api/partite/7/risultato").with(user(principal(TEL_1, false)))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.errore", containsString("set.[0].squadra1")))
                    .andExpect(jsonPath("$.timestamp", notNullValue()))
                    .andExpect(jsonPath("$.length()").value(3));
        }

        verify(partitaService, never()).registraRisultato(anyInt(), any(), anyString(), anyBoolean());
    }

    @Test
    void registraRisultatoIdPostoConDecimaliDa400SenzaChiamareIlService() throws Exception {
        String body = """
                { "squadra1": [11.5, 13], "squadra2": [12, 14],
                  "set": [ { "squadra1": 6, "squadra2": 4 }, { "squadra1": 6, "squadra2": 3 } ] }
                """;

        mockMvc.perform(post("/api/partite/7/risultato").with(user(principal(TEL_1, false)))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errore", containsString("squadra1")));

        verify(partitaService, never()).registraRisultato(anyInt(), any(), anyString(), anyBoolean());
    }

    // Senza @Valid il controller non filtra i dati: i body che Jackson non riesce a leggere devono
    // comunque dare 400 nel formato degli errori, e non 500
    @Test
    void registraRisultatoBodyNonLeggibileDa400ENon500() throws Exception {
        List<String> bodyNonLeggibili = List.of(
                "",
                "null",
                "[]",
                "{ \"squadra1\": 11, \"squadra2\": [12, 14], \"set\": [] }",
                "{ \"squadra1\": [\"abc\", 13], \"squadra2\": [12, 14], \"set\": [] }",
                "{ \"squadra1\": [11, 13], \"squadra2\": [12, 14], \"set\": { \"squadra1\": 6 } }",
                "{ \"squadra1\": [11, 13], \"squadra2\": [12, 14], \"set\": [ { \"squadra1\": \"sei\", \"squadra2\": 4 } ] }",
                "{ \"squadra1\": [11, 13], \"squadra2\": [12, 14], \"set\": [ { \"squadra1\": 99999999999, \"squadra2\": 4 } ] }");

        for (String body : bodyNonLeggibili) {
            mockMvc.perform(post("/api/partite/7/risultato").with(user(principal(TEL_1, false)))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.errore", notNullValue()))
                    .andExpect(jsonPath("$.timestamp", notNullValue()))
                    .andExpect(jsonPath("$.length()").value(3));
        }

        verify(partitaService, never()).registraRisultato(anyInt(), any(), anyString(), anyBoolean());
    }

    @Test
    void registraRisultatoSenzaAutenticazioneRifiutato() throws Exception {
        mockMvc.perform(post("/api/partite/7/risultato")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY_RISULTATO))
                .andExpect(status().isForbidden());

        verify(partitaService, never()).registraRisultato(anyInt(), any(), anyString(), anyBoolean());
    }

    private static UtentePrincipal principal(String telefono, boolean admin) {
        return new UtentePrincipal(new Utente(telefono, "Nome", "Cognome", "hash", LocalDate.of(1990, 1, 1), admin));
    }
}
