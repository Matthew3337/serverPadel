package com.padelbooking.api.service;

import com.padelbooking.api.dto.PartitaDTO;
import com.padelbooking.api.exception.AccessoNegatoException;
import com.padelbooking.api.exception.BusinessRuleException;
import com.padelbooking.api.exception.ResourceNotFoundException;
import com.padelbooking.api.exception.RichiestaNonValidaException;
import com.padelbooking.api.model.Campo;
import com.padelbooking.api.model.Partecipazione;
import com.padelbooking.api.model.Partita;
import com.padelbooking.api.model.StatoPartita;
import com.padelbooking.api.model.TipoPartecipazione;
import com.padelbooking.api.model.TipoPartita;
import com.padelbooking.api.model.Utente;
import com.padelbooking.api.repository.PartecipazioneRepository;
import com.padelbooking.api.repository.PartitaRepository;
import com.padelbooking.api.repository.UtenteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartitaServiceTest {

    private static final String TEL_CREATORE = "3330000001";
    private static final String TEL_2 = "3330000002";
    private static final String TEL_ESTRANEO = "3339999999";
    private static final ZoneId ZONA = ZoneId.of("Europe/Rome");
    // "Adesso" fisso per tutti i test
    private static final LocalDateTime ADESSO = LocalDateTime.of(2026, 11, 1, 12, 0);
    // Una fine che cade esattamente adesso: la partita è appena registrabile
    private static final LocalDateTime FINE_APPENA_RAGGIUNTA = ADESSO;

    @Mock
    private PartitaRepository partitaRepository;

    @Mock
    private PartecipazioneRepository partecipazioneRepository;

    @Mock
    private UtenteRepository utenteRepository;

    @Mock
    private PunteggiService punteggiService;

    private PartitaService service;

    private Campo campoDiurno;
    private Utente creatore;
    private Utente secondo;

    @BeforeEach
    void setUp() {
        Clock fisso = Clock.fixed(ADESSO.atZone(ZONA).toInstant(), ZONA);
        service = new PartitaService(partitaRepository, partecipazioneRepository, utenteRepository, punteggiService, fisso);
        campoDiurno = new Campo(1, "Campo 1", true, LocalTime.of(8, 0), LocalTime.of(22, 0));
        creatore = new Utente(TEL_CREATORE, "Mario", "Rossi", "x", LocalDate.of(1990, 1, 1), false, 2.5);
        secondo = new Utente(TEL_2, "Luigi", "Verdi", "x", LocalDate.of(1990, 1, 1), false, 3.0);
    }

    // ============================================
    // Visibilità
    // ============================================
    @Nested
    class Visibilita {

        @Test
        void inesistenteDa404() {
            when(partitaRepository.findById(7)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getDettaglio(7, TEL_CREATORE, false))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Partita non trovata con id 7");
        }

        @Test
        void privataNonVisibileAEstraneoConStessoMessaggio() {
            stubPartita(partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusDays(3)));

            assertThatThrownBy(() -> service.getDettaglio(7, TEL_ESTRANEO, false))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Partita non trovata con id 7");
        }

        @Test
        void pubblicaVisibileConDifferenzaLivelloUno() {
            stubPartita(partita(StatoPartita.CREATA, TipoPartita.PUBBLICA, ADESSO.plusDays(3)));
            stubEstraneo(3.5);

            PartitaDTO.DettaglioResponse r = service.getDettaglio(7, TEL_ESTRANEO, false);

            assertThat(r.getId()).isEqualTo(7);
            assertThat(r.getAzioni().getPuoUscire()).isFalse();
            assertThat(r.getAzioni().getPuoConfermare()).isFalse();
        }

        @Test
        void pubblicaNonVisibileConDifferenzaLivelloOltreUno() {
            stubPartita(partita(StatoPartita.CREATA, TipoPartita.PUBBLICA, ADESSO.plusDays(3)));
            stubEstraneo(4.0);

            assertThatThrownBy(() -> service.getDettaglio(7, TEL_ESTRANEO, false))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Partita non trovata con id 7");
        }

        @Test
        void annullataPubblicaNonVisibileAEstraneo() {
            stubPartita(partita(StatoPartita.ANNULLATA, TipoPartita.PUBBLICA, ADESSO.plusDays(3)));

            assertThatThrownBy(() -> service.getDettaglio(7, TEL_ESTRANEO, false))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void annullataVisibileAlPartecipante() {
            stubPartita(partita(StatoPartita.ANNULLATA, TipoPartita.PRIVATA, ADESSO.plusDays(3)));

            assertThat(service.getDettaglio(7, TEL_CREATORE, false).getStato()).isEqualTo("annullata");
        }

        @Test
        void privataVisibileAdAdminConAzioniTutteFalse() {
            stubPartita(partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusDays(3)));

            PartitaDTO.AzioniResponse a = service.getDettaglio(7, TEL_ESTRANEO, true).getAzioni();

            assertThat(a.getPuoInvitare()).isFalse();
            assertThat(a.getPuoEliminare()).isFalse();
            assertThat(a.getPuoUscire()).isFalse();
            assertThat(a.getPuoConfermare()).isFalse();
            assertThat(a.getPuoRegistrareRisultato()).isFalse();
        }

        @Test
        void pubblicaNonVisibileAEstraneoConTelefonoInesistente() {
            // Token di un utente non più presente in tabella: nessun livello da confrontare, quindi 404
            stubPartita(partita(StatoPartita.CREATA, TipoPartita.PUBBLICA, ADESSO.plusDays(3)));
            when(utenteRepository.findById(TEL_ESTRANEO)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getDettaglio(7, TEL_ESTRANEO, false))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Partita non trovata con id 7");
        }

        @Test
        void confermataPubblicaVisibileAEstraneoCompatibileSenzaAzioni() {
            // Fine = adesso: per un partecipante il risultato sarebbe registrabile, per l'estraneo no
            stubPartita(partita(StatoPartita.CONFERMATA, TipoPartita.PUBBLICA, ADESSO.minusMinutes(90)));
            stubEstraneo(2.0);

            PartitaDTO.DettaglioResponse r = service.getDettaglio(7, TEL_ESTRANEO, false);

            assertThat(r.getStato()).isEqualTo("confermata");
            assertTutteFalse(r.getAzioni());
        }

        @Test
        void giocataPubblicaVisibileAEstraneoCompatibileSenzaAzioni() {
            stubPartita(partita(StatoPartita.GIOCATA, TipoPartita.PUBBLICA, ADESSO.minusDays(2)));
            stubEstraneo(1.5);

            PartitaDTO.DettaglioResponse r = service.getDettaglio(7, TEL_ESTRANEO, false);

            assertThat(r.getStato()).isEqualTo("giocata");
            assertTutteFalse(r.getAzioni());
        }

        @Test
        void adminCreatoreHaAzioniTutteFalse() {
            // Come creatore oltre le 24 ore potrebbe invitare ed eliminare, ma da admin non ha azioni
            stubPartita(partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusDays(3)));

            assertTutteFalse(service.getDettaglio(7, TEL_CREATORE, true).getAzioni());
        }

        @Test
        void adminPartecipanteNonConfermatoInFinestraConfermaHaAzioniTutteFalse() {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusHours(18));
            aggiungi(p, 2, new Partecipazione(TipoPartecipazione.UTENTE, secondo, true, false));
            stubPartita(p);

            assertTutteFalse(service.getDettaglio(7, TEL_2, true).getAzioni());
        }

        @Test
        void adminPartecipanteConPartitaConfermataFinitaHaAzioniTutteFalse() {
            stubPartita(partita(StatoPartita.CONFERMATA, TipoPartita.PRIVATA, ADESSO.minusMinutes(90)));

            assertTutteFalse(service.getDettaglio(7, TEL_CREATORE, true).getAzioni());
        }
    }

    // ============================================
    // Risposta e posti
    // ============================================
    @Test
    void rispostaConCreatorePrimoEPostoCampoSenzaDati() {
        Partita p = partita(StatoPartita.CREATA, TipoPartita.PUBBLICA, ADESSO.plusDays(3));
        // Il posto CAMPO ha id minore del creatore: il creatore deve comunque essere il primo
        p.getPartecipazioni().clear();
        aggiungi(p, 1, new Partecipazione(TipoPartecipazione.CAMPO, null, false, false));
        aggiungi(p, 2, new Partecipazione(TipoPartecipazione.UTENTE, secondo, true, true));
        aggiungi(p, 3, new Partecipazione(TipoPartecipazione.UTENTE, creatore, false, false));
        stubPartita(p);

        PartitaDTO.DettaglioResponse r = service.getDettaglio(7, TEL_CREATORE, false);

        assertThat(r.getIdCampo()).isEqualTo(1);
        assertThat(r.getNomeCampo()).isEqualTo("Campo 1");
        assertThat(r.getStato()).isEqualTo("creata");
        assertThat(r.getTipo()).isEqualTo("PUBBLICA");
        assertThat(r.getTelefonoCreatore()).isEqualTo(TEL_CREATORE);
        assertThat(r.getOraFine()).isEqualTo(LocalTime.of(13, 30));
        assertThat(r.getPosti()).extracting(PartitaDTO.PostoResponse::getTipo)
                .containsExactly("UTENTE", "CAMPO", "UTENTE");
        assertThat(r.getPosti().get(0).getTelefono()).isEqualTo(TEL_CREATORE);
        assertThat(r.getPosti().get(0).getNome()).isEqualTo("Mario");
        assertThat(r.getPosti().get(0).getLivello()).isEqualTo(2.5);
        assertThat(r.getPosti().get(1).getTelefono()).isNull();
        assertThat(r.getPosti().get(1).getLivello()).isNull();
        assertThat(r.getPosti().get(2).getTelefono()).isEqualTo(TEL_2);
        assertThat(r.getPosti().get(2).getConfermata()).isTrue();
    }

    // ============================================
    // Azioni
    // ============================================
    @Nested
    class Azioni {

        @Test
        void creatoreOltre24OrePuoInvitareEEliminare() {
            stubPartita(partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusHours(24).plusMinutes(1)));

            PartitaDTO.AzioniResponse a = service.getDettaglio(7, TEL_CREATORE, false).getAzioni();

            assertThat(a.getPuoInvitare()).isTrue();
            assertThat(a.getPuoEliminare()).isTrue();
            assertThat(a.getPuoUscire()).isFalse();
            assertThat(a.getPuoConfermare()).isFalse();
            assertThat(a.getPuoRegistrareRisultato()).isFalse();
        }

        @Test
        void creatoreEsattamenteA24OreNonPuoEliminareMaPuoConfermare() {
            stubPartita(partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusHours(24)));

            PartitaDTO.AzioniResponse a = service.getDettaglio(7, TEL_CREATORE, false).getAzioni();

            assertThat(a.getPuoEliminare()).isFalse();
            assertThat(a.getPuoInvitare()).isFalse();
            assertThat(a.getPuoConfermare()).isTrue();
        }

        @Test
        void creatoreNonPuoInvitareConQuattroPosti() {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusDays(3));
            aggiungi(p, 2, new Partecipazione(TipoPartecipazione.UTENTE, secondo, true, false));
            aggiungi(p, 3, new Partecipazione(TipoPartecipazione.CAMPO, null, false, false));
            aggiungi(p, 4, new Partecipazione(TipoPartecipazione.CAMPO, null, false, false));
            stubPartita(p);

            PartitaDTO.AzioniResponse a = service.getDettaglio(7, TEL_CREATORE, false).getAzioni();

            assertThat(a.getPuoInvitare()).isFalse();
            assertThat(a.getPuoEliminare()).isTrue();
        }

        @Test
        void altroPartecipantePuoUscireMaNonEliminare() {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusDays(3));
            aggiungi(p, 2, new Partecipazione(TipoPartecipazione.UTENTE, secondo, true, false));
            stubPartita(p);

            PartitaDTO.AzioniResponse a = service.getDettaglio(7, TEL_2, false).getAzioni();

            assertThat(a.getPuoUscire()).isTrue();
            assertThat(a.getPuoEliminare()).isFalse();
            assertThat(a.getPuoInvitare()).isFalse();
        }

        @Test
        void confermaSoloTra24E12OreENonGiaConfermata() {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusHours(12).plusMinutes(1));
            aggiungi(p, 2, new Partecipazione(TipoPartecipazione.UTENTE, secondo, true, true));
            stubPartita(p);

            assertThat(service.getDettaglio(7, TEL_CREATORE, false).getAzioni().getPuoConfermare()).isTrue();
            assertThat(service.getDettaglio(7, TEL_2, false).getAzioni().getPuoConfermare()).isFalse();
        }

        @Test
        void confermaChiusaA12Ore() {
            stubPartita(partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusHours(12)));

            assertThat(service.getDettaglio(7, TEL_CREATORE, false).getAzioni().getPuoConfermare()).isFalse();
        }

        @Test
        void risultatoRegistrabileDallaFineFinoA24OreDopo() {
            // fine = adesso esatto
            stubPartita(partita(StatoPartita.CONFERMATA, TipoPartita.PRIVATA, ADESSO.minusMinutes(90)));
            assertThat(service.getDettaglio(7, TEL_CREATORE, false).getAzioni().getPuoRegistrareRisultato()).isTrue();
        }

        @Test
        void risultatoRegistrabileEsattamente24OreDopoLaFine() {
            stubPartita(partita(StatoPartita.CONFERMATA, TipoPartita.PRIVATA, ADESSO.minusHours(24).minusMinutes(90)));
            assertThat(service.getDettaglio(7, TEL_CREATORE, false).getAzioni().getPuoRegistrareRisultato()).isTrue();
        }

        @Test
        void risultatoNonRegistrabileOltre24OreDopoLaFine() {
            stubPartita(partita(StatoPartita.CONFERMATA, TipoPartita.PRIVATA, ADESSO.minusHours(24).minusMinutes(91)));
            assertThat(service.getDettaglio(7, TEL_CREATORE, false).getAzioni().getPuoRegistrareRisultato()).isFalse();
        }

        @Test
        void risultatoNonRegistrabilePrimaDellaFineOSePartitaNonConfermata() {
            stubPartita(partita(StatoPartita.CONFERMATA, TipoPartita.PRIVATA, ADESSO.minusMinutes(89)));
            assertThat(service.getDettaglio(7, TEL_CREATORE, false).getAzioni().getPuoRegistrareRisultato()).isFalse();
        }

        @Test
        void risultatoNonRegistrabileSePartitaCreata() {
            stubPartita(partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.minusMinutes(90)));
            assertThat(service.getDettaglio(7, TEL_CREATORE, false).getAzioni().getPuoRegistrareRisultato()).isFalse();
        }

        @Test
        void partitaDopoMezzanotteUsaInizioEffettivo() {
            // Campo 18:00-02:00, serata del 2 novembre, ore 00:30: inizia il 3 novembre alle 00:30,
            // cioè 36h30 dopo adesso. Con data_partita + ora sarebbe 12h30 e l'eliminazione non sarebbe ammessa.
            Campo serale = new Campo(2, "Campo Serale", false, LocalTime.of(18, 0), LocalTime.of(2, 0));
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusDays(1));
            p.setCampo(serale);
            p.setOraInizio(LocalTime.of(0, 30));
            p.setOraFine(LocalTime.of(2, 0));
            stubPartita(p);

            assertThat(service.getDettaglio(7, TEL_CREATORE, false).getAzioni().getPuoEliminare()).isTrue();
        }
    }

    // ============================================
    // Bacheca partite pubbliche
    // ============================================
    @Nested
    class Bacheca {

        @Test
        void adminRiceveListaVuotaSenzaLeggereLePartite() {
            assertThat(service.getBachecaPubbliche(TEL_ESTRANEO, true)).isEmpty();

            verifyNoInteractions(partitaRepository);
        }

        @Test
        void utenteInesistenteRiceveListaVuota() {
            when(utenteRepository.findById(TEL_ESTRANEO)).thenReturn(Optional.empty());

            assertThat(service.getBachecaPubbliche(TEL_ESTRANEO, false)).isEmpty();
        }

        @Test
        void rispostaConDatiDelCreatoreEPostiLiberi() {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PUBBLICA, ADESSO.plusDays(3));
            aggiungi(p, 2, new Partecipazione(TipoPartecipazione.CAMPO, null, false, false));
            stubEstraneo(3.5);
            stubCandidate(p);

            List<PartitaDTO.BachecaResponse> r = service.getBachecaPubbliche(TEL_ESTRANEO, false);

            assertThat(r).hasSize(1);
            PartitaDTO.BachecaResponse riga = r.get(0);
            assertThat(riga.getId()).isEqualTo(7);
            assertThat(riga.getIdCampo()).isEqualTo(1);
            assertThat(riga.getNomeCampo()).isEqualTo("Campo 1");
            assertThat(riga.getDataPartita()).isEqualTo(LocalDate.of(2026, 11, 4));
            assertThat(riga.getOraInizio()).isEqualTo(LocalTime.of(12, 0));
            assertThat(riga.getOraFine()).isEqualTo(LocalTime.of(13, 30));
            assertThat(riga.getNomeCreatore()).isEqualTo("Mario");
            assertThat(riga.getCognomeCreatore()).isEqualTo("Rossi");
            assertThat(riga.getLivelloCreatore()).isEqualTo(2.5);
            assertThat(riga.getPostiLiberi()).isEqualTo(2);
        }

        @Test
        void escludePartitaAlCompleto() {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PUBBLICA, ADESSO.plusDays(3));
            aggiungi(p, 2, new Partecipazione(TipoPartecipazione.UTENTE, secondo, false, false));
            aggiungi(p, 3, new Partecipazione(TipoPartecipazione.CAMPO, null, false, false));
            aggiungi(p, 4, new Partecipazione(TipoPartecipazione.CAMPO, null, false, false));
            stubEstraneo(2.5);
            stubCandidate(p);

            assertThat(service.getBachecaPubbliche(TEL_ESTRANEO, false)).isEmpty();
        }

        @Test
        void soglia24OreEsclusaUnMinutoPrimaInclusa() {
            Partita esattamente24 = partitaConId(7, ADESSO.plusHours(24));
            Partita oltre24 = partitaConId(8, ADESSO.plusHours(24).plusMinutes(1));
            stubEstraneo(2.5);
            stubCandidate(esattamente24, oltre24);

            assertThat(service.getBachecaPubbliche(TEL_ESTRANEO, false))
                    .extracting(PartitaDTO.BachecaResponse::getId)
                    .containsExactly(8);
        }

        @Test
        void filtraPerLivelloEntroUno() {
            stubEstraneo(4.0);
            stubCandidate(partita(StatoPartita.CREATA, TipoPartita.PUBBLICA, ADESSO.plusDays(3)));

            assertThat(service.getBachecaPubbliche(TEL_ESTRANEO, false)).isEmpty();
        }

        @Test
        void livelloInferioreDiUnoIncluso() {
            stubEstraneo(1.5);
            stubCandidate(partita(StatoPartita.CREATA, TipoPartita.PUBBLICA, ADESSO.plusDays(3)));

            assertThat(service.getBachecaPubbliche(TEL_ESTRANEO, false)).hasSize(1);
        }

        @Test
        void escludeLePartiteInCuiLUtenteGiaGioca() {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PUBBLICA, ADESSO.plusDays(3));
            aggiungi(p, 2, new Partecipazione(TipoPartecipazione.UTENTE, secondo, false, false));
            when(utenteRepository.findById(TEL_2)).thenReturn(Optional.of(secondo));
            stubCandidate(p);

            assertThat(service.getBachecaPubbliche(TEL_2, false)).isEmpty();
        }

        @Test
        void ordinataPerInizioEffettivoAncheDopoMezzanotte() {
            // Campo 18:00-02:00: la partita delle 00:30 della serata del 3 inizia il 4 alle 00:30,
            // dopo quella delle 20:00 del 3 anche se l'ora è minore
            Campo serale = new Campo(2, "Campo Serale", false, LocalTime.of(18, 0), LocalTime.of(2, 0));
            Partita dopoMezzanotte = partitaConId(7, LocalDateTime.of(2026, 11, 3, 0, 30));
            dopoMezzanotte.setCampo(serale);
            Partita sera = partitaConId(8, LocalDateTime.of(2026, 11, 3, 20, 0));
            sera.setCampo(serale);
            Partita primaDiTutte = partitaConId(9, LocalDateTime.of(2026, 11, 3, 10, 0));
            stubEstraneo(2.5);
            stubCandidate(dopoMezzanotte, sera, primaDiTutte);

            assertThat(service.getBachecaPubbliche(TEL_ESTRANEO, false))
                    .extracting(PartitaDTO.BachecaResponse::getId)
                    .containsExactly(9, 8, 7);
        }

        @Test
        void partitaDopoMezzanotteFiltrataSullInizioEffettivo() {
            // Serata del 2 novembre alle 00:30 su un campo serale: inizia il 3 alle 00:30, 36h30 dopo adesso.
            // Con data_partita + ora_inizio sarebbe 12h30 e verrebbe esclusa per errore.
            // La serata di oggi alle 00:30 inizia invece domani alle 00:30, entro le 24 ore: esclusa.
            Campo serale = new Campo(2, "Campo Serale", false, LocalTime.of(18, 0), LocalTime.of(2, 0));
            Partita domani = partitaConId(7, LocalDateTime.of(2026, 11, 2, 0, 30));
            domani.setCampo(serale);
            Partita oggi = partitaConId(8, LocalDateTime.of(2026, 11, 1, 0, 30));
            oggi.setCampo(serale);
            stubEstraneo(2.5);
            stubCandidate(domani, oggi);

            assertThat(service.getBachecaPubbliche(TEL_ESTRANEO, false))
                    .extracting(PartitaDTO.BachecaResponse::getId)
                    .containsExactly(7);
        }

        private Partita partitaConId(int id, LocalDateTime inizio) {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PUBBLICA, inizio);
            p.setId(id);
            return p;
        }

        private void stubCandidate(Partita... partite) {
            when(partitaRepository.findByTipoAndStatoDaData(TipoPartita.PUBBLICA, StatoPartita.CREATA,
                    ADESSO.toLocalDate())).thenReturn(List.of(partite));
        }
    }

    // ============================================
    // Ingresso
    // ============================================
    @Nested
    class Ingresso {

        @Test
        void adminRifiutatoCon403SenzaLeggereLaPartita() {
            assertThatThrownBy(() -> service.entra(7, TEL_ESTRANEO, true))
                    .isInstanceOf(AccessoNegatoException.class);

            verifyNoInteractions(partitaRepository);
        }

        @Test
        void inesistenteDa404() {
            when(partitaRepository.findByIdPerAggiornamento(7)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.entra(7, TEL_ESTRANEO, false))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Partita non trovata con id 7");
        }

        @Test
        void privataDa404ComeIlDettaglio() {
            stubLock(partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusDays(3)));
            stubEstraneo(2.5);

            assertThatThrownBy(() -> service.entra(7, TEL_ESTRANEO, false))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Partita non trovata con id 7");
        }

        @Test
        void livelloFuoriRangeDa404() {
            stubLock(partita(StatoPartita.CREATA, TipoPartita.PUBBLICA, ADESSO.plusDays(3)));
            stubEstraneo(4.0);

            assertThatThrownBy(() -> service.entra(7, TEL_ESTRANEO, false))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Partita non trovata con id 7");
        }

        @Test
        void annullataDa404() {
            stubLock(partita(StatoPartita.ANNULLATA, TipoPartita.PUBBLICA, ADESSO.plusDays(3)));
            stubEstraneo(2.5);

            assertThatThrownBy(() -> service.entra(7, TEL_ESTRANEO, false))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void giaPartecipanteDa409AncheSePrivata() {
            stubLock(partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusDays(3)));

            assertThatThrownBy(() -> service.entra(7, TEL_CREATORE, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Partecipi già a questa partita");
        }

        @Test
        void confermataDa409NonPiuAperta() {
            stubLock(partita(StatoPartita.CONFERMATA, TipoPartita.PUBBLICA, ADESSO.plusDays(3)));
            stubEstraneo(2.5);

            assertThatThrownBy(() -> service.entra(7, TEL_ESTRANEO, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("La partita non è più aperta");
        }

        @Test
        void alCompletoDa409() {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PUBBLICA, ADESSO.plusDays(3));
            aggiungi(p, 2, new Partecipazione(TipoPartecipazione.UTENTE, secondo, false, false));
            aggiungi(p, 3, new Partecipazione(TipoPartecipazione.CAMPO, null, false, false));
            aggiungi(p, 4, new Partecipazione(TipoPartecipazione.CAMPO, null, false, false));
            stubLock(p);
            stubEstraneo(2.5);

            assertThatThrownBy(() -> service.entra(7, TEL_ESTRANEO, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("La partita è al completo");
        }

        @Test
        void esattamente24OrePrimaDa409IngressoChiuso() {
            stubLock(partita(StatoPartita.CREATA, TipoPartita.PUBBLICA, ADESSO.plusHours(24)));
            stubEstraneo(2.5);

            assertThatThrownBy(() -> service.entra(7, TEL_ESTRANEO, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("L'ingresso è chiuso: mancano meno di 24 ore all'inizio");
            verify(partitaRepository, never()).flush();
        }

        @Test
        void ingressoAggiungePartecipazioneUtenteNonInvitataNonConfermata() {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PUBBLICA, ADESSO.plusHours(24).plusMinutes(1));
            stubLock(p);
            Utente estraneo = stubEstraneo(3.5);

            PartitaDTO.DettaglioResponse r = service.entra(7, TEL_ESTRANEO, false);

            verify(partitaRepository).flush();
            assertThat(p.getPartecipazioni()).hasSize(2);
            Partecipazione nuova = p.getPartecipazioni().get(1);
            assertThat(nuova.getTipo()).isEqualTo(TipoPartecipazione.UTENTE);
            assertThat(nuova.getUtente()).isSameAs(estraneo);
            assertThat(nuova.getPartita()).isSameAs(p);
            assertThat(nuova.getInvitato()).isFalse();
            assertThat(nuova.getConfermata()).isFalse();

            assertThat(r.getId()).isEqualTo(7);
            assertThat(r.getPosti()).extracting(PartitaDTO.PostoResponse::getTelefono)
                    .containsExactly(TEL_CREATORE, TEL_ESTRANEO);
            assertThat(r.getAzioni().getPuoUscire()).isTrue();
            assertThat(r.getAzioni().getPuoEliminare()).isFalse();
            assertThat(r.getAzioni().getPuoInvitare()).isFalse();
        }

        @Test
        void ingressoSullUltimoPostoAmmesso() {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PUBBLICA, ADESSO.plusDays(3));
            aggiungi(p, 2, new Partecipazione(TipoPartecipazione.UTENTE, secondo, false, false));
            aggiungi(p, 3, new Partecipazione(TipoPartecipazione.CAMPO, null, false, false));
            stubLock(p);
            stubEstraneo(2.5);

            assertThat(service.entra(7, TEL_ESTRANEO, false).getPosti()).hasSize(4);
        }

        @Test
        void vincoloUnicoSulDbDiventaPartecipiGia() {
            stubLock(partita(StatoPartita.CREATA, TipoPartita.PUBBLICA, ADESSO.plusDays(3)));
            stubEstraneo(2.5);
            doThrow(new DataIntegrityViolationException(
                    "Duplicate entry '7-3339999999' for key 'partecipazione.uk_partecipazione_utente'"))
                    .when(partitaRepository).flush();

            assertThatThrownBy(() -> service.entra(7, TEL_ESTRANEO, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Partecipi già a questa partita");
        }

        @Test
        void altraViolazioneDiIntegritaRilanciata() {
            stubLock(partita(StatoPartita.CREATA, TipoPartita.PUBBLICA, ADESSO.plusDays(3)));
            stubEstraneo(2.5);
            doThrow(new DataIntegrityViolationException("altro vincolo")).when(partitaRepository).flush();

            assertThatThrownBy(() -> service.entra(7, TEL_ESTRANEO, false))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void nonUsaFindByIdSenzaLock() {
            stubLock(partita(StatoPartita.CREATA, TipoPartita.PUBBLICA, ADESSO.plusDays(3)));
            stubEstraneo(2.5);

            service.entra(7, TEL_ESTRANEO, false);

            verify(partitaRepository, never()).findById(any());
        }
    }

    // ============================================
    // Eliminazione
    // ============================================
    @Nested
    class Eliminazione {

        @Test
        void adminRifiutatoCon403SenzaLeggereLaPartita() {
            assertThatThrownBy(() -> service.elimina(7, TEL_CREATORE, true))
                    .isInstanceOf(AccessoNegatoException.class)
                    .hasMessage("L'amministratore non può eliminare le partite da qui");

            verifyNoInteractions(partitaRepository);
        }

        @Test
        void inesistenteDa404() {
            when(partitaRepository.findByIdPerAggiornamento(7)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.elimina(7, TEL_CREATORE, false))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Partita non trovata con id 7");
        }

        @Test
        void privataNonVisibileDa404() {
            stubLock(partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusDays(3)));
            stubEstraneo(2.5);

            assertThatThrownBy(() -> service.elimina(7, TEL_ESTRANEO, false))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Partita non trovata con id 7");
        }

        @Test
        void estraneoCheVedeLaPubblicaDa403() {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PUBBLICA, ADESSO.plusDays(3));
            stubLock(p);
            stubEstraneo(2.5);

            assertThatThrownBy(() -> service.elimina(7, TEL_ESTRANEO, false))
                    .isInstanceOf(AccessoNegatoException.class)
                    .hasMessage("Solo il creatore può eliminare la partita");
            assertThat(p.getStato()).isEqualTo(StatoPartita.CREATA);
        }

        @Test
        void partecipanteNonCreatoreDa403() {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusDays(3));
            aggiungi(p, 2, new Partecipazione(TipoPartecipazione.UTENTE, secondo, true, false));
            stubLock(p);

            assertThatThrownBy(() -> service.elimina(7, TEL_2, false))
                    .isInstanceOf(AccessoNegatoException.class)
                    .hasMessage("Solo il creatore può eliminare la partita");
        }

        @Test
        void confermataDa409() {
            Partita p = partita(StatoPartita.CONFERMATA, TipoPartita.PRIVATA, ADESSO.plusDays(3));
            stubLock(p);

            assertThatThrownBy(() -> service.elimina(7, TEL_CREATORE, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("La partita non è più aperta");
            assertThat(p.getStato()).isEqualTo(StatoPartita.CONFERMATA);
        }

        @Test
        void giaAnnullataDa409() {
            stubLock(partita(StatoPartita.ANNULLATA, TipoPartita.PUBBLICA, ADESSO.plusDays(3)));

            assertThatThrownBy(() -> service.elimina(7, TEL_CREATORE, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("La partita non è più aperta");
        }

        @Test
        void esattamente24OrePrimaDa409EStatoInvariato() {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PUBBLICA, ADESSO.plusHours(24));
            stubLock(p);

            assertThatThrownBy(() -> service.elimina(7, TEL_CREATORE, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("L'eliminazione è chiusa: mancano meno di 24 ore all'inizio. "
                            + "Solo l'amministratore può annullare la partita");
            assertThat(p.getStato()).isEqualTo(StatoPartita.CREATA);
        }

        @Test
        void unSecondoOltreLe24OreAnnullaLaPartitaSenzaToccareIPosti() {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PUBBLICA, ADESSO.plusHours(24).plusSeconds(1));
            aggiungi(p, 2, new Partecipazione(TipoPartecipazione.UTENTE, secondo, false, false));
            stubLock(p);

            service.elimina(7, TEL_CREATORE, false);

            assertThat(p.getStato()).isEqualTo(StatoPartita.ANNULLATA);
            assertThat(p.getPartecipazioni()).hasSize(2);
        }

        @Test
        void dopoMezzanotteContaLInizioEffettivo() {
            // Campo 18:00-02:00, serata del 2 novembre alle 00:30: inizia il 3 alle 00:30, 36h30 dopo adesso.
            // Con data_partita + ora_inizio sarebbero 12h30 e l'eliminazione verrebbe rifiutata per errore
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PRIVATA, LocalDateTime.of(2026, 11, 2, 0, 30));
            p.setCampo(new Campo(2, "Campo Serale", false, LocalTime.of(18, 0), LocalTime.of(2, 0)));
            stubLock(p);

            service.elimina(7, TEL_CREATORE, false);

            assertThat(p.getStato()).isEqualTo(StatoPartita.ANNULLATA);
        }

        @Test
        void nonUsaFindByIdSenzaLock() {
            stubLock(partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusDays(3)));

            service.elimina(7, TEL_CREATORE, false);

            verify(partitaRepository, never()).findById(any());
        }
    }

    // ============================================
    // Uscita
    // ============================================
    @Nested
    class Uscita {

        @Test
        void adminRifiutatoCon403SenzaLeggereLaPartita() {
            assertThatThrownBy(() -> service.esci(7, TEL_2, true))
                    .isInstanceOf(AccessoNegatoException.class)
                    .hasMessage("L'amministratore non può uscire dalle partite");

            verifyNoInteractions(partitaRepository);
        }

        @Test
        void inesistenteDa404() {
            when(partitaRepository.findByIdPerAggiornamento(7)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.esci(7, TEL_2, false))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Partita non trovata con id 7");
        }

        @Test
        void privataNonVisibileDa404() {
            stubLock(partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusDays(3)));
            stubEstraneo(2.5);

            assertThatThrownBy(() -> service.esci(7, TEL_ESTRANEO, false))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Partita non trovata con id 7");
        }

        @Test
        void estraneoCheVedeLaPubblicaDa409NonPartecipi() {
            stubLock(partita(StatoPartita.CREATA, TipoPartita.PUBBLICA, ADESSO.plusDays(3)));
            stubEstraneo(2.5);

            assertThatThrownBy(() -> service.esci(7, TEL_ESTRANEO, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Non partecipi a questa partita");
        }

        @Test
        void creatoreDa409() {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PUBBLICA, ADESSO.plusDays(3));
            stubLock(p);

            assertThatThrownBy(() -> service.esci(7, TEL_CREATORE, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Il creatore non può uscire dalla partita: può eliminarla");
            assertThat(p.getPartecipazioni()).hasSize(1);
        }

        @Test
        void confermataDa409() {
            Partita p = partita(StatoPartita.CONFERMATA, TipoPartita.PRIVATA, ADESSO.plusDays(3));
            aggiungi(p, 2, new Partecipazione(TipoPartecipazione.UTENTE, secondo, true, true));
            stubLock(p);

            assertThatThrownBy(() -> service.esci(7, TEL_2, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("La partita non è più aperta");
            assertThat(p.getPartecipazioni()).hasSize(2);
        }

        @Test
        void annullataDa409AncheSePrivata() {
            // Il partecipante vede sempre la partita, anche annullata: quindi 409 e non 404
            Partita p = partita(StatoPartita.ANNULLATA, TipoPartita.PRIVATA, ADESSO.plusDays(3));
            aggiungi(p, 2, new Partecipazione(TipoPartecipazione.UTENTE, secondo, true, false));
            stubLock(p);

            assertThatThrownBy(() -> service.esci(7, TEL_2, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("La partita non è più aperta");
        }

        @Test
        void esattamente24OrePrimaDa409EPostoInvariato() {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PUBBLICA, ADESSO.plusHours(24));
            aggiungi(p, 2, new Partecipazione(TipoPartecipazione.UTENTE, secondo, false, false));
            stubLock(p);

            assertThatThrownBy(() -> service.esci(7, TEL_2, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("L'uscita è chiusa: mancano meno di 24 ore all'inizio");
            assertThat(p.getPartecipazioni()).hasSize(2);
        }

        @Test
        void unSecondoOltreLe24OreRimuoveSoloLaSuaPartecipazioneEStatoInvariato() {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PUBBLICA, ADESSO.plusHours(24).plusSeconds(1));
            Partecipazione mia = new Partecipazione(TipoPartecipazione.UTENTE, secondo, false, false);
            aggiungi(p, 2, mia);
            aggiungi(p, 3, new Partecipazione(TipoPartecipazione.CAMPO, null, false, false));
            stubLock(p);

            service.esci(7, TEL_2, false);

            assertThat(p.getStato()).isEqualTo(StatoPartita.CREATA);
            assertThat(p.getPartecipazioni()).extracting(Partecipazione::getId).containsExactly(1, 3);
            // Scollegata dalla partita: con orphanRemoval il flush ne cancella la riga
            assertThat(mia.getPartita()).isNull();
        }

        @Test
        void invitatoInPrivataPuoUscire() {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusDays(3));
            aggiungi(p, 2, new Partecipazione(TipoPartecipazione.UTENTE, secondo, true, false));
            stubLock(p);

            service.esci(7, TEL_2, false);

            assertThat(p.getPartecipazioni()).hasSize(1);
        }

        @Test
        void nonUsaFindByIdSenzaLock() {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PUBBLICA, ADESSO.plusDays(3));
            aggiungi(p, 2, new Partecipazione(TipoPartecipazione.UTENTE, secondo, false, false));
            stubLock(p);

            service.esci(7, TEL_2, false);

            verify(partitaRepository, never()).findById(any());
        }
    }

    // ============================================
    // Coerenza tra azioni del dettaglio ed endpoint
    // ============================================
    // Per ogni scenario puoEliminare / puoUscire di GET /api/partite/{id} devono valere true
    // esattamente quando elimina / esci vanno a buon fine
    @Nested
    class CoerenzaAzioni {

        @Test
        void puoEliminareCoincideConElimina() {
            for (StatoPartita stato : StatoPartita.values()) {
                for (LocalDateTime inizio : List.of(ADESSO.plusHours(24), ADESSO.plusHours(24).plusSeconds(1))) {
                    for (String telefono : List.of(TEL_CREATORE, TEL_2)) {
                        Partita dettaglio = scenario(stato, inizio);
                        stubPartita(dettaglio);
                        boolean puo = service.getDettaglio(7, telefono, false).getAzioni().getPuoEliminare();

                        Partita perAzione = scenario(stato, inizio);
                        when(partitaRepository.findByIdPerAggiornamento(7)).thenReturn(Optional.of(perAzione));
                        boolean riuscita = riesce(() -> service.elimina(7, telefono, false));

                        assertThat(riuscita).as("%s, inizio %s, %s", stato, inizio, telefono).isEqualTo(puo);
                    }
                }
            }
        }

        @Test
        void puoUscireCoincideConEsci() {
            for (StatoPartita stato : StatoPartita.values()) {
                for (LocalDateTime inizio : List.of(ADESSO.plusHours(24), ADESSO.plusHours(24).plusSeconds(1))) {
                    for (String telefono : List.of(TEL_CREATORE, TEL_2)) {
                        Partita dettaglio = scenario(stato, inizio);
                        stubPartita(dettaglio);
                        boolean puo = service.getDettaglio(7, telefono, false).getAzioni().getPuoUscire();

                        Partita perAzione = scenario(stato, inizio);
                        when(partitaRepository.findByIdPerAggiornamento(7)).thenReturn(Optional.of(perAzione));
                        boolean riuscita = riesce(() -> service.esci(7, telefono, false));

                        assertThat(riuscita).as("%s, inizio %s, %s", stato, inizio, telefono).isEqualTo(puo);
                    }
                }
            }
        }

        // Partita privata con creatore e secondo giocatore: entrambi la vedono da partecipanti
        private Partita scenario(StatoPartita stato, LocalDateTime inizio) {
            Partita p = partita(stato, TipoPartita.PRIVATA, inizio);
            aggiungi(p, 2, new Partecipazione(TipoPartecipazione.UTENTE, secondo, true, false));
            return p;
        }

        private boolean riesce(Runnable azione) {
            try {
                azione.run();
                return true;
            } catch (AccessoNegatoException | BusinessRuleException ex) {
                return false;
            }
        }
    }

    // ============================================
    // Storico
    // ============================================
    @Nested
    class Storico {

        @Test
        void adminRiceveListaVuotaSenzaInterrogareIlDb() {
            assertThat(service.getStorico(TEL_ESTRANEO, true)).isEmpty();

            verifyNoInteractions(partecipazioneRepository);
        }

        @Test
        void usaLaQueryConUtenteEdEscludeAnnullate() {
            when(partecipazioneRepository.findStoricoByTelefono(TEL_CREATORE, TipoPartecipazione.UTENTE,
                    StatoPartita.ANNULLATA)).thenReturn(List.of());

            assertThat(service.getStorico(TEL_CREATORE, false)).isEmpty();

            verify(partecipazioneRepository).findStoricoByTelefono(TEL_CREATORE, TipoPartecipazione.UTENTE,
                    StatoPartita.ANNULLATA);
            verifyNoInteractions(partitaRepository);
        }

        @Test
        void formatoDellaRigaSenzaRisultato() {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PRIVATA, LocalDateTime.of(2026, 11, 5, 18, 0));
            stubStorico(p.getPartecipazioni().get(0));

            List<PartitaDTO.StoricoResponse> r = service.getStorico(TEL_CREATORE, false);

            assertThat(r).hasSize(1);
            PartitaDTO.StoricoResponse riga = r.get(0);
            assertThat(riga.getId()).isEqualTo(7);
            assertThat(riga.getIdCampo()).isEqualTo(1);
            assertThat(riga.getNomeCampo()).isEqualTo("Campo 1");
            assertThat(riga.getDataPartita()).isEqualTo(LocalDate.of(2026, 11, 5));
            assertThat(riga.getOraInizio()).isEqualTo(LocalTime.of(18, 0));
            assertThat(riga.getOraFine()).isEqualTo(LocalTime.of(19, 30));
            assertThat(riga.getStato()).isEqualTo("creata");
            assertThat(riga.getTipo()).isEqualTo("PRIVATA");
            assertThat(riga.getRisultato()).isNull();
        }

        @Test
        void unSoloSetNonERisultatoRegistrato() {
            Partita p = partita(StatoPartita.CONFERMATA, TipoPartita.PUBBLICA, ADESSO.minusDays(1));
            p.setSet1Sq1(6);
            p.setSet1Sq2(4);
            stubStorico(p.getPartecipazioni().get(0));

            assertThat(service.getStorico(TEL_CREATORE, false).get(0).getRisultato()).isNull();
        }

        @Test
        void dueSetVintiDallaSquadraDellUtente() {
            Partecipazione pa = giocataConSet(1, 12, 6, 4, 6, 3, null, null);

            PartitaDTO.RisultatoResponse r = service.getStorico(TEL_CREATORE, false).get(0).getRisultato();

            assertThat(r.getSet()).hasSize(2);
            assertThat(r.getSet().get(0).getSquadra1()).isEqualTo(6);
            assertThat(r.getSet().get(0).getSquadra2()).isEqualTo(4);
            assertThat(r.getSet().get(1).getSquadra1()).isEqualTo(6);
            assertThat(r.getSet().get(1).getSquadra2()).isEqualTo(3);
            assertThat(r.getSquadraUtente()).isEqualTo(1);
            assertThat(r.getEsito()).isEqualTo("VINTA");
            assertThat(r.getPuntiAssegnati()).isEqualTo(12);
            assertThat(pa.getPartita().getStato()).isEqualTo(StatoPartita.GIOCATA);
        }

        @Test
        void dueSetPersiDallaSquadraDellUtente() {
            giocataConSet(1, -8, 2, 6, 4, 6, null, null);

            PartitaDTO.RisultatoResponse r = service.getStorico(TEL_CREATORE, false).get(0).getRisultato();

            assertThat(r.getSet()).hasSize(2);
            assertThat(r.getEsito()).isEqualTo("PERSA");
            assertThat(r.getPuntiAssegnati()).isEqualTo(-8);
        }

        @Test
        void treSetVintiDallaSquadraUno() {
            giocataConSet(1, 12, 6, 4, 3, 6, 6, 2);

            PartitaDTO.RisultatoResponse r = service.getStorico(TEL_CREATORE, false).get(0).getRisultato();

            assertThat(r.getSet()).hasSize(3);
            assertThat(r.getSet().get(2).getSquadra1()).isEqualTo(6);
            assertThat(r.getSet().get(2).getSquadra2()).isEqualTo(2);
            assertThat(r.getEsito()).isEqualTo("VINTA");
        }

        @Test
        void treSetVintiDallaSquadraDueVistiDallaSquadraDue() {
            giocataConSet(2, 10, 6, 4, 3, 6, 2, 6);

            PartitaDTO.RisultatoResponse r = service.getStorico(TEL_CREATORE, false).get(0).getRisultato();

            assertThat(r.getSet()).hasSize(3);
            assertThat(r.getSquadraUtente()).isEqualTo(2);
            assertThat(r.getEsito()).isEqualTo("VINTA");
        }

        @Test
        void treSetPersiDallaSquadraDellUtente() {
            giocataConSet(2, -5, 6, 4, 3, 6, 6, 2);

            assertThat(service.getStorico(TEL_CREATORE, false).get(0).getRisultato().getEsito()).isEqualTo("PERSA");
        }

        @Test
        void senzaSquadraEsitoESquadraNullMaSetPresenti() {
            giocataConSet(null, null, 6, 4, 6, 3, null, null);

            PartitaDTO.RisultatoResponse r = service.getStorico(TEL_CREATORE, false).get(0).getRisultato();

            assertThat(r.getSet()).hasSize(2);
            assertThat(r.getSquadraUtente()).isNull();
            assertThat(r.getEsito()).isNull();
            assertThat(r.getPuntiAssegnati()).isNull();
        }

        @Test
        void puntiNonAncoraCalcolatiSonoNull() {
            giocataConSet(1, null, 6, 4, 6, 3, null, null);

            PartitaDTO.RisultatoResponse r = service.getStorico(TEL_CREATORE, false).get(0).getRisultato();

            assertThat(r.getEsito()).isEqualTo("VINTA");
            assertThat(r.getPuntiAssegnati()).isNull();
        }

        @Test
        void setVintiPariRisultatoPresenteConEsitoNull() {
            // Lo schema ammette 1-1 senza terzo set: il risultato si mostra, ma senza un vincitore
            // l'esito resta null invece di essere indovinato
            giocataConSet(1, null, 6, 4, 4, 6, null, null);

            PartitaDTO.RisultatoResponse r = service.getStorico(TEL_CREATORE, false).get(0).getRisultato();

            assertThat(r).isNotNull();
            assertThat(r.getSet()).hasSize(2);
            assertThat(r.getSquadraUtente()).isEqualTo(1);
            assertThat(r.getEsito()).isNull();
        }

        @Test
        void statoConfermataInMinuscoloETipoPubblica() {
            Partita p = partita(StatoPartita.CONFERMATA, TipoPartita.PUBBLICA, ADESSO.plusHours(10));
            stubStorico(p.getPartecipazioni().get(0));

            PartitaDTO.StoricoResponse riga = service.getStorico(TEL_CREATORE, false).get(0);

            assertThat(riga.getStato()).isEqualTo("confermata");
            assertThat(riga.getTipo()).isEqualTo("PUBBLICA");
            assertThat(riga.getRisultato()).isNull();
        }

        @Test
        void ordinatoPerInizioEffettivoDecrescenteAncheDopoMezzanotte() {
            // Campo aperto dalle 18:00 alle 02:00: lo slot delle 00:30 della serata del 5 inizia il 6
            Campo notturno = new Campo(2, "Campo notte", true, LocalTime.of(18, 0), LocalTime.of(2, 0));
            Partita serata = partitaConId(10, notturno, LocalDate.of(2026, 11, 5), LocalTime.of(18, 0));
            Partita dopoMezzanotte = partitaConId(11, notturno, LocalDate.of(2026, 11, 5), LocalTime.of(0, 30));
            Partita passata = partitaConId(12, campoDiurno, LocalDate.of(2026, 10, 20), LocalTime.of(10, 0));
            Partita futura = partitaConId(13, campoDiurno, LocalDate.of(2026, 11, 20), LocalTime.of(10, 0));
            // Stesso inizio di "serata" su un altro campo: a parità vince l'id più alto
            Partita stessoInizio = partitaConId(14, campoDiurno, LocalDate.of(2026, 11, 5), LocalTime.of(18, 0));
            when(partecipazioneRepository.findStoricoByTelefono(TEL_CREATORE, TipoPartecipazione.UTENTE,
                    StatoPartita.ANNULLATA)).thenReturn(List.of(
                    serata.getPartecipazioni().get(0), passata.getPartecipazioni().get(0),
                    dopoMezzanotte.getPartecipazioni().get(0), stessoInizio.getPartecipazioni().get(0),
                    futura.getPartecipazioni().get(0)));

            List<Integer> ids = service.getStorico(TEL_CREATORE, false).stream()
                    .map(PartitaDTO.StoricoResponse::getId)
                    .toList();

            assertThat(ids).containsExactly(13, 11, 14, 10, 12);
        }

        // Partita giocata con i set indicati (null = set non giocato) e la squadra e i punti del creatore
        private Partecipazione giocataConSet(Integer squadra, Integer punti, Integer s1a, Integer s1b,
                                             Integer s2a, Integer s2b, Integer s3a, Integer s3b) {
            Partita p = partita(StatoPartita.GIOCATA, TipoPartita.PUBBLICA, ADESSO.minusDays(2));
            p.setSet1Sq1(s1a);
            p.setSet1Sq2(s1b);
            p.setSet2Sq1(s2a);
            p.setSet2Sq2(s2b);
            p.setSet3Sq1(s3a);
            p.setSet3Sq2(s3b);
            Partecipazione pa = p.getPartecipazioni().get(0);
            pa.setSquadra(squadra);
            pa.setPuntiAssegnati(punti);
            stubStorico(pa);
            return pa;
        }

        private Partita partitaConId(int id, Campo campo, LocalDate data, LocalTime ora) {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PUBBLICA, LocalDateTime.of(data, ora));
            p.setId(id);
            p.setCampo(campo);
            return p;
        }

        private void stubStorico(Partecipazione pa) {
            when(partecipazioneRepository.findStoricoByTelefono(TEL_CREATORE, TipoPartecipazione.UTENTE,
                    StatoPartita.ANNULLATA)).thenReturn(List.of(pa));
        }
    }

    // ============================================
    // Utenti invitabili
    // ============================================
    @Nested
    class Invitabili {

        @Test
        void adminRifiutatoCon403SenzaLeggereLaPartita() {
            assertThatThrownBy(() -> service.getInvitabili(7, null, TEL_CREATORE, true))
                    .isInstanceOf(AccessoNegatoException.class)
                    .hasMessage("Solo il creatore può invitare");

            verifyNoInteractions(partitaRepository, utenteRepository);
        }

        @Test
        void inesistenteDa404() {
            when(partitaRepository.findById(7)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getInvitabili(7, null, TEL_CREATORE, false))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Partita non trovata con id 7");
        }

        @Test
        void privataNonVisibileDa404() {
            stubPartita(partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusDays(3)));
            stubEstraneo(2.5);

            assertThatThrownBy(() -> service.getInvitabili(7, null, TEL_ESTRANEO, false))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Partita non trovata con id 7");
        }

        @Test
        void estraneoCheVedeLaPubblicaDa403() {
            stubPartita(partita(StatoPartita.CREATA, TipoPartita.PUBBLICA, ADESSO.plusDays(3)));
            stubEstraneo(2.5);

            assertThatThrownBy(() -> service.getInvitabili(7, null, TEL_ESTRANEO, false))
                    .isInstanceOf(AccessoNegatoException.class)
                    .hasMessage("Solo il creatore può invitare");
        }

        @Test
        void partecipanteNonCreatoreDa403() {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusDays(3));
            aggiungi(p, 2, new Partecipazione(TipoPartecipazione.UTENTE, secondo, true, false));
            stubPartita(p);

            assertThatThrownBy(() -> service.getInvitabili(7, null, TEL_2, false))
                    .isInstanceOf(AccessoNegatoException.class)
                    .hasMessage("Solo il creatore può invitare");
            verify(utenteRepository, never()).findInvitabili(any(), any());
        }

        @Test
        void creatoreRiceveGliUtentiDellaQueryNelFormatoDelContratto() {
            stubPartita(partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusDays(3)));
            Utente anna = new Utente(TEL_ESTRANEO, "Anna", "Bianchi", "x", LocalDate.of(1990, 1, 1), false, 5.0);
            when(utenteRepository.findInvitabili(7, "%%")).thenReturn(List.of(anna, secondo));

            List<PartitaDTO.InvitabileResponse> r = service.getInvitabili(7, null, TEL_CREATORE, false);

            // L'ordine è quello della query (cognome, nome): il service non lo cambia
            assertThat(r).extracting(PartitaDTO.InvitabileResponse::getTelefono).containsExactly(TEL_ESTRANEO, TEL_2);
            PartitaDTO.InvitabileResponse prima = r.get(0);
            assertThat(prima.getNome()).isEqualTo("Anna");
            assertThat(prima.getCognome()).isEqualTo("Bianchi");
            assertThat(prima.getLivello()).isEqualTo(5.0);
        }

        @Test
        void ricercaVuotaOSoloSpaziCorrispondeATutti() {
            stubPartita(partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusDays(3)));
            when(utenteRepository.findInvitabili(7, "%%")).thenReturn(List.of());

            service.getInvitabili(7, "   ", TEL_CREATORE, false);

            verify(utenteRepository).findInvitabili(7, "%%");
        }

        @Test
        void ricercaInMinuscoloSenzaSpaziAiLati() {
            stubPartita(partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusDays(3)));
            when(utenteRepository.findInvitabili(7, "%ros%")).thenReturn(List.of());

            service.getInvitabili(7, "  RoS ", TEL_CREATORE, false);

            verify(utenteRepository).findInvitabili(7, "%ros%");
        }

        @Test
        void caratteriSpecialiDiLikeCercatiComeCaratteriNormali() {
            stubPartita(partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusDays(3)));
            when(utenteRepository.findInvitabili(7, "%a!%b!_c!!%")).thenReturn(List.of());

            service.getInvitabili(7, "a%b_c!", TEL_CREATORE, false);

            verify(utenteRepository).findInvitabili(7, "%a!%b!_c!!%");
        }

        @Test
        void consentitoAncheAPartitaChiusaAgliInviti() {
            // Il contratto prevede solo 404 e 403: l'elenco non dipende da stato e orario,
            // sono invita e puoInvitare a bloccare l'invito
            stubPartita(partita(StatoPartita.CONFERMATA, TipoPartita.PRIVATA, ADESSO.plusHours(13)));
            when(utenteRepository.findInvitabili(7, "%%")).thenReturn(List.of(secondo));

            assertThat(service.getInvitabili(7, null, TEL_CREATORE, false)).hasSize(1);
        }
    }

    // ============================================
    // Invito
    // ============================================
    @Nested
    class Invito {

        @Test
        void adminRifiutatoCon403SenzaLeggereLaPartita() {
            assertThatThrownBy(() -> service.invita(7, TEL_2, TEL_CREATORE, true))
                    .isInstanceOf(AccessoNegatoException.class)
                    .hasMessage("Solo il creatore può invitare");

            verifyNoInteractions(partitaRepository, utenteRepository);
        }

        @Test
        void inesistenteDa404() {
            when(partitaRepository.findByIdPerAggiornamento(7)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.invita(7, TEL_2, TEL_CREATORE, false))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Partita non trovata con id 7");
        }

        @Test
        void privataNonVisibileDa404() {
            stubLock(partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusDays(3)));
            stubEstraneo(2.5);

            assertThatThrownBy(() -> service.invita(7, TEL_2, TEL_ESTRANEO, false))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Partita non trovata con id 7");
        }

        @Test
        void estraneoCheVedeLaPubblicaDa403() {
            stubLock(partita(StatoPartita.CREATA, TipoPartita.PUBBLICA, ADESSO.plusDays(3)));
            stubEstraneo(2.5);

            assertThatThrownBy(() -> service.invita(7, TEL_2, TEL_ESTRANEO, false))
                    .isInstanceOf(AccessoNegatoException.class)
                    .hasMessage("Solo il creatore può invitare");
        }

        @Test
        void partecipanteNonCreatoreDa403EPostiInvariati() {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusDays(3));
            aggiungi(p, 2, new Partecipazione(TipoPartecipazione.UTENTE, secondo, true, false));
            stubLock(p);

            assertThatThrownBy(() -> service.invita(7, TEL_ESTRANEO, TEL_2, false))
                    .isInstanceOf(AccessoNegatoException.class)
                    .hasMessage("Solo il creatore può invitare");
            assertThat(p.getPartecipazioni()).hasSize(2);
        }

        @Test
        void confermataDa409NonPiuAperta() {
            stubLock(partita(StatoPartita.CONFERMATA, TipoPartita.PRIVATA, ADESSO.plusDays(3)));

            assertThatThrownBy(() -> service.invita(7, TEL_2, TEL_CREATORE, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("La partita non è più aperta");
        }

        @Test
        void annullataDa409NonPiuAperta() {
            stubLock(partita(StatoPartita.ANNULLATA, TipoPartita.PRIVATA, ADESSO.plusDays(3)));

            assertThatThrownBy(() -> service.invita(7, TEL_2, TEL_CREATORE, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("La partita non è più aperta");
        }

        @Test
        void esattamente24OrePrimaDa409InvitiChiusi() {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusHours(24));
            stubLock(p);

            assertThatThrownBy(() -> service.invita(7, TEL_2, TEL_CREATORE, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Gli inviti sono chiusi: mancano meno di 24 ore all'inizio");
            assertThat(p.getPartecipazioni()).hasSize(1);
            verify(partitaRepository, never()).flush();
        }

        @Test
        void unSecondoOltreLe24OreInvitoAmmesso() {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusHours(24).plusSeconds(1));
            stubLock(p);
            when(utenteRepository.findById(TEL_2)).thenReturn(Optional.of(secondo));

            service.invita(7, TEL_2, TEL_CREATORE, false);

            assertThat(p.getPartecipazioni()).hasSize(2);
        }

        @Test
        void dopoMezzanotteContaLInizioEffettivo() {
            // Campo 18:00-02:00, serata del 2 novembre alle 00:30: inizia il 3 alle 00:30, 36h30 dopo adesso.
            // Con data_partita + ora_inizio sarebbero 12h30 e l'invito verrebbe rifiutato per errore
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PRIVATA, LocalDateTime.of(2026, 11, 2, 0, 30));
            p.setCampo(new Campo(2, "Campo Serale", false, LocalTime.of(18, 0), LocalTime.of(2, 0)));
            stubLock(p);
            when(utenteRepository.findById(TEL_2)).thenReturn(Optional.of(secondo));

            service.invita(7, TEL_2, TEL_CREATORE, false);

            assertThat(p.getPartecipazioni()).hasSize(2);
        }

        @Test
        void alCompletoDa409() {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusDays(3));
            aggiungi(p, 2, new Partecipazione(TipoPartecipazione.UTENTE, secondo, true, false));
            aggiungi(p, 3, new Partecipazione(TipoPartecipazione.CAMPO, null, false, false));
            aggiungi(p, 4, new Partecipazione(TipoPartecipazione.CAMPO, null, false, false));
            stubLock(p);

            assertThatThrownBy(() -> service.invita(7, TEL_ESTRANEO, TEL_CREATORE, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("La partita è al completo");
            assertThat(p.getPartecipazioni()).hasSize(4);
        }

        @Test
        void utenteInesistenteDa404() {
            stubLock(partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusDays(3)));
            when(utenteRepository.findById(TEL_ESTRANEO)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.invita(7, TEL_ESTRANEO, TEL_CREATORE, false))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Utente non trovato");
        }

        @Test
        void adminInvitatoDa409() {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusDays(3));
            stubLock(p);
            Utente admin = new Utente(TEL_ESTRANEO, "Admin", "Circolo", "x", LocalDate.of(1990, 1, 1), true);
            when(utenteRepository.findById(TEL_ESTRANEO)).thenReturn(Optional.of(admin));

            assertThatThrownBy(() -> service.invita(7, TEL_ESTRANEO, TEL_CREATORE, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Non è possibile invitare un amministratore");
            assertThat(p.getPartecipazioni()).hasSize(1);
        }

        @Test
        void utenteGiaPartecipanteDa409() {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusDays(3));
            aggiungi(p, 2, new Partecipazione(TipoPartecipazione.UTENTE, secondo, false, false));
            stubLock(p);
            when(utenteRepository.findById(TEL_2)).thenReturn(Optional.of(secondo));

            assertThatThrownBy(() -> service.invita(7, TEL_2, TEL_CREATORE, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("L'utente partecipa già a questa partita");
            assertThat(p.getPartecipazioni()).hasSize(2);
        }

        @Test
        void creatoreCheInvitaSeStessoDa409() {
            stubLock(partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusDays(3)));
            when(utenteRepository.findById(TEL_CREATORE)).thenReturn(Optional.of(creatore));

            assertThatThrownBy(() -> service.invita(7, TEL_CREATORE, TEL_CREATORE, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("L'utente partecipa già a questa partita");
        }

        @Test
        void invitoAggiungePartecipazioneUtenteInvitataNonConfermata() {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusDays(3));
            stubLock(p);
            when(utenteRepository.findById(TEL_2)).thenReturn(Optional.of(secondo));

            PartitaDTO.DettaglioResponse r = service.invita(7, TEL_2, TEL_CREATORE, false);

            verify(partitaRepository).flush();
            assertThat(p.getPartecipazioni()).hasSize(2);
            Partecipazione nuova = p.getPartecipazioni().get(1);
            assertThat(nuova.getTipo()).isEqualTo(TipoPartecipazione.UTENTE);
            assertThat(nuova.getUtente()).isSameAs(secondo);
            assertThat(nuova.getPartita()).isSameAs(p);
            assertThat(nuova.getInvitato()).isTrue();
            assertThat(nuova.getConfermata()).isFalse();

            // Il dettaglio è quello del creatore: può ancora invitare ed eliminare
            assertThat(r.getId()).isEqualTo(7);
            assertThat(r.getPosti()).extracting(PartitaDTO.PostoResponse::getTelefono)
                    .containsExactly(TEL_CREATORE, TEL_2);
            assertThat(r.getAzioni().getPuoInvitare()).isTrue();
            assertThat(r.getAzioni().getPuoEliminare()).isTrue();
            assertThat(r.getAzioni().getPuoUscire()).isFalse();
        }

        @Test
        void invitoInPubblicaDiQualunqueLivello() {
            // RF_GI_2: l'invito ignora il limite di ±1 della bacheca (creatore 2.5, invitato 5.0)
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PUBBLICA, ADESSO.plusDays(3));
            stubLock(p);
            Utente lontano = new Utente(TEL_ESTRANEO, "Anna", "Bianchi", "x", LocalDate.of(1990, 1, 1), false, 5.0);
            when(utenteRepository.findById(TEL_ESTRANEO)).thenReturn(Optional.of(lontano));

            service.invita(7, TEL_ESTRANEO, TEL_CREATORE, false);

            assertThat(p.getPartecipazioni().get(1).getUtente()).isSameAs(lontano);
            assertThat(p.getPartecipazioni().get(1).getInvitato()).isTrue();
        }

        @Test
        void invitoSullUltimoPostoAmmessoEPoiPuoInvitareFalse() {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusDays(3));
            aggiungi(p, 2, new Partecipazione(TipoPartecipazione.UTENTE, secondo, true, false));
            aggiungi(p, 3, new Partecipazione(TipoPartecipazione.CAMPO, null, false, false));
            stubLock(p);
            stubEstraneo(2.5);

            PartitaDTO.DettaglioResponse r = service.invita(7, TEL_ESTRANEO, TEL_CREATORE, false);

            assertThat(r.getPosti()).hasSize(4);
            assertThat(r.getAzioni().getPuoInvitare()).isFalse();
            assertThat(r.getAzioni().getPuoEliminare()).isTrue();
        }

        @Test
        void vincoloUnicoSulDbDiventaUtenteGiaPartecipante() {
            stubLock(partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusDays(3)));
            when(utenteRepository.findById(TEL_2)).thenReturn(Optional.of(secondo));
            doThrow(new DataIntegrityViolationException(
                    "Duplicate entry '7-3330000002' for key 'partecipazione.uk_partecipazione_utente'"))
                    .when(partitaRepository).flush();

            assertThatThrownBy(() -> service.invita(7, TEL_2, TEL_CREATORE, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("L'utente partecipa già a questa partita");
        }

        @Test
        void altraViolazioneDiIntegritaRilanciata() {
            stubLock(partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusDays(3)));
            when(utenteRepository.findById(TEL_2)).thenReturn(Optional.of(secondo));
            doThrow(new DataIntegrityViolationException("altro vincolo")).when(partitaRepository).flush();

            assertThatThrownBy(() -> service.invita(7, TEL_2, TEL_CREATORE, false))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void nonUsaFindByIdSenzaLock() {
            stubLock(partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusDays(3)));
            when(utenteRepository.findById(TEL_2)).thenReturn(Optional.of(secondo));

            service.invita(7, TEL_2, TEL_CREATORE, false);

            verify(partitaRepository, never()).findById(any());
        }
    }

    // ============================================
    // Coerenza tra puoInvitare e invita
    // ============================================
    // Per ogni combinazione di stato, istante (limite esatto delle 24 h e un secondo prima),
    // richiedente e posti occupati, puoInvitare vale true esattamente quando invita va a buon fine
    @Nested
    class CoerenzaInviti {

        @Test
        void puoInvitareCoincideConInvita() {
            // L'invitato esiste ed è invitabile: conta solo la parte di regole condivisa con il dettaglio
            lenient().when(utenteRepository.findById(TEL_ESTRANEO)).thenReturn(Optional.of(
                    new Utente(TEL_ESTRANEO, "Anna", "Bianchi", "x", LocalDate.of(1990, 1, 1), false, 2.5)));
            for (StatoPartita stato : StatoPartita.values()) {
                for (LocalDateTime inizio : List.of(ADESSO.plusHours(24), ADESSO.plusHours(24).plusSeconds(1))) {
                    for (String telefono : List.of(TEL_CREATORE, TEL_2)) {
                        for (boolean completa : List.of(false, true)) {
                            stubPartita(scenario(stato, inizio, completa));
                            boolean puo = service.getDettaglio(7, telefono, false).getAzioni().getPuoInvitare();

                            stubLock(scenario(stato, inizio, completa));
                            boolean riuscito = riesce(() -> service.invita(7, TEL_ESTRANEO, telefono, false));

                            assertThat(riuscito).as("%s, inizio %s, %s, completa %s", stato, inizio, telefono,
                                    completa).isEqualTo(puo);
                        }
                    }
                }
            }
        }

        // Partita privata con creatore e secondo giocatore, più due posti CAMPO se completa
        private Partita scenario(StatoPartita stato, LocalDateTime inizio, boolean completa) {
            Partita p = partita(stato, TipoPartita.PRIVATA, inizio);
            aggiungi(p, 2, new Partecipazione(TipoPartecipazione.UTENTE, secondo, true, false));
            if (completa) {
                aggiungi(p, 3, new Partecipazione(TipoPartecipazione.CAMPO, null, false, false));
                aggiungi(p, 4, new Partecipazione(TipoPartecipazione.CAMPO, null, false, false));
            }
            return p;
        }

        private boolean riesce(Runnable azione) {
            try {
                azione.run();
                return true;
            } catch (AccessoNegatoException | BusinessRuleException ex) {
                return false;
            }
        }
    }

    // ============================================
    // Bacheca inviti
    // ============================================
    @Nested
    class BachecaInviti {

        @Test
        void adminRiceveListaVuotaSenzaInterrogareIlDb() {
            assertThat(service.getBachecaInviti(TEL_2, true)).isEmpty();

            verifyNoInteractions(partecipazioneRepository, partitaRepository);
        }

        @Test
        void usaLaQueryConUtenteECreataOConfermata() {
            when(partecipazioneRepository.findInvitiByTelefono(TEL_2, TipoPartecipazione.UTENTE,
                    List.of(StatoPartita.CREATA, StatoPartita.CONFERMATA))).thenReturn(List.of());

            assertThat(service.getBachecaInviti(TEL_2, false)).isEmpty();

            verify(partecipazioneRepository).findInvitiByTelefono(TEL_2, TipoPartecipazione.UTENTE,
                    List.of(StatoPartita.CREATA, StatoPartita.CONFERMATA));
            verifyNoInteractions(partitaRepository);
        }

        @Test
        void formatoDellaRiga() {
            Partita p = partita(StatoPartita.CONFERMATA, TipoPartita.PRIVATA, LocalDateTime.of(2026, 11, 5, 18, 0));
            Partecipazione invito = new Partecipazione(TipoPartecipazione.UTENTE, secondo, true, false);
            aggiungi(p, 2, invito);
            stubInviti(invito);

            List<PartitaDTO.InvitoRicevutoResponse> r = service.getBachecaInviti(TEL_2, false);

            assertThat(r).hasSize(1);
            PartitaDTO.InvitoRicevutoResponse riga = r.get(0);
            assertThat(riga.getId()).isEqualTo(7);
            assertThat(riga.getIdCampo()).isEqualTo(1);
            assertThat(riga.getNomeCampo()).isEqualTo("Campo 1");
            assertThat(riga.getDataPartita()).isEqualTo(LocalDate.of(2026, 11, 5));
            assertThat(riga.getOraInizio()).isEqualTo(LocalTime.of(18, 0));
            assertThat(riga.getOraFine()).isEqualTo(LocalTime.of(19, 30));
            assertThat(riga.getStato()).isEqualTo("confermata");
            assertThat(riga.getTipo()).isEqualTo("PRIVATA");
            assertThat(riga.getNomeCreatore()).isEqualTo("Mario");
            assertThat(riga.getCognomeCreatore()).isEqualTo("Rossi");
        }

        @Test
        void ordinataPerInizioEffettivoCrescenteAncheDopoMezzanotte() {
            // Campo aperto dalle 18:00 alle 02:00: lo slot delle 00:30 della serata del 5 inizia il 6
            Campo notturno = new Campo(2, "Campo notte", true, LocalTime.of(18, 0), LocalTime.of(2, 0));
            Partecipazione serata = invitoIn(10, notturno, LocalDate.of(2026, 11, 5), LocalTime.of(18, 0));
            Partecipazione dopoMezzanotte = invitoIn(11, notturno, LocalDate.of(2026, 11, 5), LocalTime.of(0, 30));
            Partecipazione prima = invitoIn(12, campoDiurno, LocalDate.of(2026, 11, 3), LocalTime.of(10, 0));
            Partecipazione dopo = invitoIn(13, campoDiurno, LocalDate.of(2026, 11, 20), LocalTime.of(10, 0));
            // Stesso inizio di "serata" su un altro campo: a parità viene prima l'id più basso
            Partecipazione stessoInizio = invitoIn(9, campoDiurno, LocalDate.of(2026, 11, 5), LocalTime.of(18, 0));
            stubInviti(serata, dopo, dopoMezzanotte, stessoInizio, prima);

            List<Integer> ids = service.getBachecaInviti(TEL_2, false).stream()
                    .map(PartitaDTO.InvitoRicevutoResponse::getId)
                    .toList();

            assertThat(ids).containsExactly(12, 9, 10, 11, 13);
        }

        private Partecipazione invitoIn(int id, Campo campo, LocalDate data, LocalTime ora) {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PRIVATA, LocalDateTime.of(data, ora));
            p.setId(id);
            p.setCampo(campo);
            Partecipazione invito = new Partecipazione(TipoPartecipazione.UTENTE, secondo, true, false);
            aggiungi(p, 2, invito);
            return invito;
        }

        private void stubInviti(Partecipazione... inviti) {
            when(partecipazioneRepository.findInvitiByTelefono(TEL_2, TipoPartecipazione.UTENTE,
                    List.of(StatoPartita.CREATA, StatoPartita.CONFERMATA))).thenReturn(List.of(inviti));
        }
    }

    // ============================================
    // Conferma
    // ============================================
    @Nested
    class Conferma {

        private static final String FUORI_FINESTRA = "La conferma è possibile solo tra 24 e 12 ore prima dell'inizio";

        @Test
        void adminRiceve403SenzaLeggereLaPartita() {
            assertThatThrownBy(() -> service.conferma(7, TEL_CREATORE, true))
                    .isInstanceOf(AccessoNegatoException.class)
                    .hasMessage("L'amministratore non conferma le partite");
            verifyNoInteractions(partitaRepository);
        }

        @Test
        void inesistenteDa404() {
            when(partitaRepository.findByIdPerAggiornamento(7)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.conferma(7, TEL_CREATORE, false))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Partita non trovata con id 7");
        }

        @Test
        void privataNonVisibileAEstraneoDa404() {
            stubLock(partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusHours(20)));

            assertThatThrownBy(() -> service.conferma(7, TEL_ESTRANEO, false))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Partita non trovata con id 7");
        }

        @Test
        void estraneoSuPubblicaVisibileNonPartecipa() {
            stubLock(partita(StatoPartita.CREATA, TipoPartita.PUBBLICA, ADESSO.plusHours(20)));
            stubEstraneo(2.5);

            assertThatThrownBy(() -> service.conferma(7, TEL_ESTRANEO, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Non partecipi a questa partita");
        }

        @Test
        void giaConfermatoDa409() {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusHours(20));
            p.getPartecipazioni().get(0).setConfermata(true);
            stubLock(p);

            assertThatThrownBy(() -> service.conferma(7, TEL_CREATORE, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Hai già confermato la partecipazione");
        }

        @Test
        void partitaNonCreataDa409() {
            stubLock(partita(StatoPartita.ANNULLATA, TipoPartita.PRIVATA, ADESSO.plusHours(20)));

            assertThatThrownBy(() -> service.conferma(7, TEL_CREATORE, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("La partita non è più aperta");
        }

        @Test
        void esattamenteA24OreLaFinestraEAperta() {
            stubLock(partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusHours(24)));

            PartitaDTO.DettaglioResponse r = service.conferma(7, TEL_CREATORE, false);

            assertThat(r.getPosti().get(0).getConfermata()).isTrue();
        }

        @Test
        void unSecondoPrimaDelle24OreLaFinestraEChiusa() {
            stubLock(partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusHours(24).plusSeconds(1)));

            assertThatThrownBy(() -> service.conferma(7, TEL_CREATORE, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage(FUORI_FINESTRA);
        }

        @Test
        void unSecondoPrimaDelle12OreLaFinestraEAncoraAperta() {
            stubLock(partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusHours(12).plusSeconds(1)));

            assertThat(service.conferma(7, TEL_CREATORE, false).getPosti().get(0).getConfermata()).isTrue();
        }

        @Test
        void esattamenteA12OreLaFinestraEChiusa() {
            stubLock(partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusHours(12)));

            assertThatThrownBy(() -> service.conferma(7, TEL_CREATORE, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage(FUORI_FINESTRA);
        }

        @Test
        void confermaConPostiLiberiLasciaLaPartitaCreata() {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusHours(20));
            aggiungi(p, 2, new Partecipazione(TipoPartecipazione.UTENTE, secondo, true, true));
            aggiungi(p, 3, new Partecipazione(TipoPartecipazione.CAMPO, null, false, false));
            stubLock(p);

            PartitaDTO.DettaglioResponse r = service.conferma(7, TEL_CREATORE, false);

            assertThat(p.getPartecipazioni().get(0).getConfermata()).isTrue();
            assertThat(r.getStato()).isEqualTo("creata");
            assertThat(r.getAzioni().getPuoConfermare()).isFalse();
        }

        @Test
        void ultimoUtenteCheConfermaRendeLaPartitaConfermataAncheConPostiCampoNonConfermati() {
            // RF_GC_5, RF_GC_6: due utenti e due posti CAMPO, che non confermano mai
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusHours(20));
            p.getPartecipazioni().get(0).setConfermata(true);
            aggiungi(p, 2, new Partecipazione(TipoPartecipazione.UTENTE, secondo, true, false));
            aggiungi(p, 3, new Partecipazione(TipoPartecipazione.CAMPO, null, false, false));
            aggiungi(p, 4, new Partecipazione(TipoPartecipazione.CAMPO, null, false, false));
            stubLock(p);

            PartitaDTO.DettaglioResponse r = service.conferma(7, TEL_2, false);

            assertThat(p.getStato()).isEqualTo(StatoPartita.CONFERMATA);
            assertThat(r.getStato()).isEqualTo("confermata");
            assertThat(r.getPosti()).extracting(PartitaDTO.PostoResponse::getConfermata)
                    .containsExactly(true, true, false, false);
            assertTutteFalse(r.getAzioni());
        }

        @Test
        void con4PostiMaUnUtenteNonConfermatoRestaCreata() {
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusHours(20));
            aggiungi(p, 2, new Partecipazione(TipoPartecipazione.UTENTE, secondo, true, false));
            aggiungi(p, 3, new Partecipazione(TipoPartecipazione.CAMPO, null, false, false));
            aggiungi(p, 4, new Partecipazione(TipoPartecipazione.CAMPO, null, false, false));
            stubLock(p);

            PartitaDTO.DettaglioResponse r = service.conferma(7, TEL_CREATORE, false);

            assertThat(p.getStato()).isEqualTo(StatoPartita.CREATA);
            assertThat(r.getStato()).isEqualTo("creata");
        }

        @Test
        void partitaDopoMezzanotteUsaInizioEffettivo() {
            // Campo 18:00-02:00, serata del 1° novembre, ore 00:30: inizia il 2 novembre alle 00:30,
            // fra 12h30, quindi nella finestra. Con data_partita + ora sarebbe già iniziata.
            Campo serale = new Campo(2, "Campo Serale", false, LocalTime.of(18, 0), LocalTime.of(2, 0));
            Partita p = partita(StatoPartita.CREATA, TipoPartita.PRIVATA, LocalDateTime.of(2026, 11, 1, 0, 30));
            p.setCampo(serale);
            p.setOraFine(LocalTime.of(2, 0));
            stubLock(p);

            assertThat(service.conferma(7, TEL_CREATORE, false).getPosti().get(0).getConfermata()).isTrue();
        }

        @Test
        void nonUsaFindByIdSenzaLock() {
            stubLock(partita(StatoPartita.CREATA, TipoPartita.PRIVATA, ADESSO.plusHours(20)));

            service.conferma(7, TEL_CREATORE, false);

            verify(partitaRepository, never()).findById(any());
        }
    }

    // ============================================
    // Coerenza tra puoConfermare e conferma
    // ============================================
    @Nested
    class CoerenzaConferma {

        @Test
        void puoConfermareCoincideConConferma() {
            List<LocalDateTime> inizi = List.of(ADESSO.plusHours(24).plusSeconds(1), ADESSO.plusHours(24),
                    ADESSO.plusHours(12).plusSeconds(1), ADESSO.plusHours(12));
            for (StatoPartita stato : StatoPartita.values()) {
                for (LocalDateTime inizio : inizi) {
                    for (String telefono : List.of(TEL_CREATORE, TEL_2, TEL_ESTRANEO)) {
                        for (boolean giaConfermato : List.of(false, true)) {
                            stubPartita(scenario(stato, inizio, giaConfermato));
                            boolean puo = puoConfermareOppureNonVisibile(telefono);

                            stubLock(scenario(stato, inizio, giaConfermato));
                            boolean riuscita = riesce(() -> service.conferma(7, telefono, false));

                            assertThat(riuscita).as("%s, inizio %s, %s, già confermato %s", stato, inizio,
                                    telefono, giaConfermato).isEqualTo(puo);
                        }
                    }
                }
            }
        }

        // L'estraneo vede la pubblica (stesso livello del creatore) ma non partecipa;
        // se il dettaglio non è visibile, anche la conferma deve fallire
        private boolean puoConfermareOppureNonVisibile(String telefono) {
            try {
                return service.getDettaglio(7, telefono, false).getAzioni().getPuoConfermare();
            } catch (ResourceNotFoundException ex) {
                return false;
            }
        }

        // Partita pubblica con creatore, secondo giocatore e un posto CAMPO
        private Partita scenario(StatoPartita stato, LocalDateTime inizio, boolean giaConfermato) {
            Partita p = partita(stato, TipoPartita.PUBBLICA, inizio);
            p.getPartecipazioni().get(0).setConfermata(giaConfermato);
            aggiungi(p, 2, new Partecipazione(TipoPartecipazione.UTENTE, secondo, false, giaConfermato));
            aggiungi(p, 3, new Partecipazione(TipoPartecipazione.CAMPO, null, false, false));
            return p;
        }

        private boolean riesce(Runnable azione) {
            try {
                azione.run();
                return true;
            } catch (AccessoNegatoException | BusinessRuleException | ResourceNotFoundException ex) {
                return false;
            }
        }

        @BeforeEach
        void stubEstraneoStessoLivello() {
            lenient().when(utenteRepository.findById(TEL_ESTRANEO)).thenReturn(Optional.of(
                    new Utente(TEL_ESTRANEO, "Anna", "Bianchi", "x", LocalDate.of(1990, 1, 1), false, 2.5)));
        }
    }

    // ============================================
    // Registrazione del risultato
    // ============================================
    @Nested
    class RegistrazioneRisultato {

        private static final String NUMERO_SET = "Il risultato deve avere 2 o 3 set e una squadra deve vincerne esattamente 2";
        private static final String SOLO_CONFERMATE = "Il risultato si può registrare solo per le partite confermate";
        private static final String NON_FINITA = "La partita non è ancora finita";
        private static final String SCADUTO = "Il termine per registrare il risultato è scaduto";
        private static final String GIA_REGISTRATO = "Il risultato è già stato registrato";
        private static final String NON_PARTECIPI = "Non partecipi a questa partita";
        private static final String SQUADRE_POSTI = "Le squadre devono contenere i 4 posti della partita";
        private static final String GIOCATORI_PER_SQUADRA = "Ogni squadra deve avere 2 giocatori";

        // ------ Regole di accesso ------

        @Test
        void adminRiceve403SenzaLeggereLaPartita() {
            assertThatThrownBy(() -> service.registraRisultato(7, duePerDue(), TEL_CREATORE, true))
                    .isInstanceOf(AccessoNegatoException.class)
                    .hasMessage("L'amministratore modifica i risultati dalla propria area");
            verifyNoInteractions(partitaRepository);
        }

        @Test
        void inesistenteDa404() {
            when(partitaRepository.findByIdPerAggiornamento(7)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.registraRisultato(7, duePerDue(), TEL_CREATORE, false))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Partita non trovata con id 7");
        }

        @Test
        void privataNonVisibileAEstraneoDa404AncheConBodyNonValido() {
            Partita p = daRegistrare(StatoPartita.CONFERMATA, FINE_APPENA_RAGGIUNTA);
            p.setTipo(TipoPartita.PRIVATA);
            stubLock(p);

            assertThatThrownBy(() -> service.registraRisultato(7, new PartitaDTO.RisultatoRequest(), TEL_ESTRANEO, false))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Partita non trovata con id 7");
        }

        @Test
        void estraneoSuPubblicaVisibileNonPartecipa() {
            stubLock(daRegistrare(StatoPartita.CONFERMATA, FINE_APPENA_RAGGIUNTA));
            stubEstraneo(2.5);

            assertThatThrownBy(() -> service.registraRisultato(7, duePerDue(), TEL_ESTRANEO, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage(NON_PARTECIPI);
        }

        @Test
        void usaIlLockInScrittura() {
            stubLock(daRegistrare(StatoPartita.CONFERMATA, FINE_APPENA_RAGGIUNTA));

            service.registraRisultato(7, duePerDue(), TEL_CREATORE, false);

            verify(partitaRepository).findByIdPerAggiornamento(7);
            verify(partitaRepository, never()).findById(any());
        }

        // ------ Stato della partita ------

        @Test
        void giocataDa409GiaRegistrato() {
            stubLock(daRegistrare(StatoPartita.GIOCATA, FINE_APPENA_RAGGIUNTA));

            assertThatThrownBy(() -> service.registraRisultato(7, duePerDue(), TEL_CREATORE, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage(GIA_REGISTRATO);
        }

        @Test
        void confermataConSetGiaPresentiDa409GiaRegistrato() {
            Partita p = daRegistrare(StatoPartita.CONFERMATA, FINE_APPENA_RAGGIUNTA);
            p.setSet1Sq1(6);
            p.setSet1Sq2(0);
            p.setSet2Sq1(6);
            p.setSet2Sq2(0);
            stubLock(p);

            assertThatThrownBy(() -> service.registraRisultato(7, duePerDue(), TEL_CREATORE, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage(GIA_REGISTRATO);
            assertThat(p.getSet1Sq2()).isZero();
        }

        @Test
        void secondaRegistrazioneDa409ENonSovrascriveLaPrima() {
            // Con il lock la seconda richiesta legge la partita lasciata dalla prima
            Partita p = daRegistrare(StatoPartita.CONFERMATA, FINE_APPENA_RAGGIUNTA);
            stubLock(p);
            service.registraRisultato(7, duePerDue(), TEL_CREATORE, false);

            PartitaDTO.RisultatoRequest altra = new PartitaDTO.RisultatoRequest(List.of(2, 3), List.of(1, 4),
                    List.of(new PartitaDTO.SetRequest(0, 6), new PartitaDTO.SetRequest(0, 6)));
            assertThatThrownBy(() -> service.registraRisultato(7, altra, TEL_2, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage(GIA_REGISTRATO);

            assertThat(p.getRegistrante()).isSameAs(creatore);
            assertThat(p.getSet1Sq1()).isEqualTo(6);
            assertThat(p.getPartecipazioni().get(0).getSquadra()).isEqualTo(1);
        }

        @Test
        void creataDa409SoloConfermate() {
            stubLock(daRegistrare(StatoPartita.CREATA, FINE_APPENA_RAGGIUNTA));

            assertThatThrownBy(() -> service.registraRisultato(7, duePerDue(), TEL_CREATORE, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage(SOLO_CONFERMATE);
        }

        @Test
        void annullataDa409SoloConfermatePerChiPartecipa() {
            stubLock(daRegistrare(StatoPartita.ANNULLATA, FINE_APPENA_RAGGIUNTA));

            assertThatThrownBy(() -> service.registraRisultato(7, duePerDue(), TEL_CREATORE, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage(SOLO_CONFERMATE);
        }

        @Test
        void iRifiutiSulContestoVengonoPrimaDellaValidazioneDelBody() {
            stubLock(daRegistrare(StatoPartita.CONFERMATA, ADESSO.plusHours(1)));

            assertThatThrownBy(() -> service.registraRisultato(7, new PartitaDTO.RisultatoRequest(), TEL_CREATORE, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage(NON_FINITA);
        }

        // ------ Limiti della finestra [fine, fine + 24 h] ------

        @Test
        void unSecondoPrimaDellaFineNonEAncoraFinita() {
            Partita p = daRegistrare(StatoPartita.CONFERMATA, ADESSO.plusSeconds(1));
            stubLock(p);

            assertThatThrownBy(() -> service.registraRisultato(7, duePerDue(), TEL_CREATORE, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage(NON_FINITA);
            assertNienteScritto(p);
        }

        @Test
        void esattamenteAllaFineLaFinestraEAperta() {
            stubLock(daRegistrare(StatoPartita.CONFERMATA, ADESSO));

            assertThat(service.registraRisultato(7, duePerDue(), TEL_CREATORE, false).getStato()).isEqualTo("giocata");
        }

        @Test
        void esattamente24OreDopoLaFineLaFinestraEAncoraAperta() {
            stubLock(daRegistrare(StatoPartita.CONFERMATA, ADESSO.minusHours(24)));

            assertThat(service.registraRisultato(7, duePerDue(), TEL_CREATORE, false).getStato()).isEqualTo("giocata");
        }

        @Test
        void unSecondoOltreLe24OreIlTermineEScaduto() {
            Partita p = daRegistrare(StatoPartita.CONFERMATA, ADESSO.minusHours(24).minusSeconds(1));
            stubLock(p);

            assertThatThrownBy(() -> service.registraRisultato(7, duePerDue(), TEL_CREATORE, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage(SCADUTO);
            assertNienteScritto(p);
        }

        @Test
        void partitaDopoMezzanotteUsaLaFineEffettiva() {
            // Serata del 31/10, 00:30-02:00: finisce l'1/11 alle 02:00, quindi adesso (12:00) è in finestra.
            // Con la sola data_partita la fine sarebbe il 31/10 alle 02:00 e il termine risulterebbe scaduto
            stubLock(dopoMezzanotte(LocalDate.of(2026, 10, 31)));

            assertThat(service.registraRisultato(7, duePerDue(), TEL_CREATORE, false).getStato()).isEqualTo("giocata");
        }

        @Test
        void partitaDopoMezzanotteDellaSerataDiOggiNonEAncoraFinita() {
            // Serata dell'1/11, 00:30-02:00: si gioca il 2/11, anche se data e ora di fine sono già passate
            stubLock(dopoMezzanotte(LocalDate.of(2026, 11, 1)));

            assertThatThrownBy(() -> service.registraRisultato(7, duePerDue(), TEL_CREATORE, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage(NON_FINITA);
        }

        // ------ Squadre ------

        @Test
        void squadreMancantiOConNumeroSbagliato() {
            List<List<List<Integer>>> casi = List.of(
                    List.of(List.of(1), List.of(2, 3, 4)),
                    List.of(List.of(1, 2, 3), List.of(4)),
                    List.of(List.of(), List.of(1, 2)),
                    List.of(List.of(1, 2, 3, 4), List.of()));
            for (List<List<Integer>> caso : casi) {
                assertRichiestaNonValida(new PartitaDTO.RisultatoRequest(caso.get(0), caso.get(1), dueSet()),
                        GIOCATORI_PER_SQUADRA);
            }
            assertRichiestaNonValida(new PartitaDTO.RisultatoRequest(null, List.of(2, 4), dueSet()), GIOCATORI_PER_SQUADRA);
            assertRichiestaNonValida(new PartitaDTO.RisultatoRequest(List.of(1, 3), null, dueSet()), GIOCATORI_PER_SQUADRA);
        }

        @Test
        void squadreConIdRipetutiONonDellaPartita() {
            assertRichiestaNonValida(new PartitaDTO.RisultatoRequest(List.of(1, 1), List.of(2, 3), dueSet()), SQUADRE_POSTI);
            // Lo stesso posto in entrambe le squadre
            assertRichiestaNonValida(new PartitaDTO.RisultatoRequest(List.of(1, 2), List.of(2, 3), dueSet()), SQUADRE_POSTI);
            assertRichiestaNonValida(new PartitaDTO.RisultatoRequest(List.of(1, 3), List.of(2, 99), dueSet()), SQUADRE_POSTI);
            assertRichiestaNonValida(new PartitaDTO.RisultatoRequest(Arrays.asList(1, null), List.of(2, 4), dueSet()), SQUADRE_POSTI);
        }

        @Test
        void partitaConfermataConMenoDi4PostiNonPassaLaValidazione() {
            Partita p = daRegistrare(StatoPartita.CONFERMATA, FINE_APPENA_RAGGIUNTA);
            p.rimuoviPartecipazione(p.getPartecipazioni().get(3));
            stubLock(p);

            PartitaDTO.RisultatoRequest ripetuto = new PartitaDTO.RisultatoRequest(List.of(1, 2), List.of(3, 3), dueSet());
            assertThatThrownBy(() -> service.registraRisultato(7, ripetuto, TEL_CREATORE, false))
                    .isInstanceOf(RichiestaNonValidaException.class)
                    .hasMessage(SQUADRE_POSTI);
            assertNienteScritto(p);
        }

        // ------ Set: i casi dettagliati stanno in RegoleRisultatoPartitaTest ------

        @Test
        void setNonValidoIndicaQualeSet() {
            assertRichiestaNonValida(conSet(new PartitaDTO.SetRequest(6, 4), new PartitaDTO.SetRequest(6, 5)),
                    "Il set 2 non è valido: si vince a 6 game con almeno 2 di scarto, oppure 7-5 o 7-6");
            assertRichiestaNonValida(conSet(new PartitaDTO.SetRequest(3, 3), new PartitaDTO.SetRequest(6, 4)),
                    "Il set 1 non è valido: si vince a 6 game con almeno 2 di scarto, oppure 7-5 o 7-6");
            // Un elemento null nella lista, o un game mancante, non devono diventare un 500
            assertRichiestaNonValida(conSet(new PartitaDTO.SetRequest(6, 4), null),
                    "Il set 2 non è valido: si vince a 6 game con almeno 2 di scarto, oppure 7-5 o 7-6");
            assertRichiestaNonValida(conSet(new PartitaDTO.SetRequest(6, null), new PartitaDTO.SetRequest(6, 4)),
                    "Il set 1 non è valido: si vince a 6 game con almeno 2 di scarto, oppure 7-5 o 7-6");
        }

        @Test
        void setSopraIlMassimoDiPrimaSonoAccettati() {
            Partita p = daRegistrare(StatoPartita.CONFERMATA, FINE_APPENA_RAGGIUNTA);
            stubLock(p);

            PartitaDTO.DettaglioResponse r = service.registraRisultato(7, conSet(new PartitaDTO.SetRequest(7, 6),
                    new PartitaDTO.SetRequest(5, 7), new PartitaDTO.SetRequest(7, 5)), TEL_CREATORE, false);

            assertThat(r.getStato()).isEqualTo("giocata");
            assertThat(p.getSet1Sq1()).isEqualTo(7);
            assertThat(p.getSet1Sq2()).isEqualTo(6);
            assertThat(p.getSet2Sq2()).isEqualTo(7);
            assertThat(p.getSet3Sq1()).isEqualTo(7);
        }

        @Test
        void numeroDiSetEVincitore() {
            assertRichiestaNonValida(new PartitaDTO.RisultatoRequest(List.of(1, 3), List.of(2, 4), null), NUMERO_SET);
            assertRichiestaNonValida(conSet(), NUMERO_SET);
            assertRichiestaNonValida(conSet(new PartitaDTO.SetRequest(6, 4)), NUMERO_SET);
            assertRichiestaNonValida(conSet(new PartitaDTO.SetRequest(6, 4), new PartitaDTO.SetRequest(4, 6)), NUMERO_SET);
        }

        @Test
        void terzoSetDiTroppo() {
            assertRichiestaNonValida(conSet(new PartitaDTO.SetRequest(6, 4), new PartitaDTO.SetRequest(6, 3),
                            new PartitaDTO.SetRequest(2, 6)),
                    "Il terzo set non va inserito se una squadra ha vinto i primi due");
        }

        // ------ Effetto ------

        @Test
        void dueSetLascianoNullIlTerzoESalvanoTutto() {
            Partita p = daRegistrare(StatoPartita.CONFERMATA, FINE_APPENA_RAGGIUNTA);
            stubLock(p);

            PartitaDTO.DettaglioResponse r = service.registraRisultato(7, duePerDue(), TEL_2, false);

            assertThat(p.getSet1Sq1()).isEqualTo(6);
            assertThat(p.getSet1Sq2()).isEqualTo(4);
            assertThat(p.getSet2Sq1()).isEqualTo(6);
            assertThat(p.getSet2Sq2()).isEqualTo(3);
            // ck_partita_set: con 2 set entrambe le colonne del terzo restano NULL
            assertThat(p.getSet3Sq1()).isNull();
            assertThat(p.getSet3Sq2()).isNull();
            assertThat(p.getRegistrante()).isSameAs(secondo);
            assertThat(p.getDataRisultato()).isEqualTo(ADESSO);
            assertThat(p.getStato()).isEqualTo(StatoPartita.GIOCATA);

            assertThat(r.getStato()).isEqualTo("giocata");
            assertThat(r.getRisultato().getSet()).hasSize(2);
            assertThat(r.getRisultato().getSquadraUtente()).isEqualTo(2);
            assertThat(r.getRisultato().getEsito()).isEqualTo("PERSA");
            assertThat(r.getRisultato().getPuntiAssegnati()).isNull();
            assertTutteFalse(r.getAzioni());
        }

        @Test
        void treSetSalvanoTuttiIGame() {
            Partita p = daRegistrare(StatoPartita.CONFERMATA, FINE_APPENA_RAGGIUNTA);
            stubLock(p);

            PartitaDTO.DettaglioResponse r = service.registraRisultato(7, conSet(new PartitaDTO.SetRequest(6, 4),
                    new PartitaDTO.SetRequest(3, 6), new PartitaDTO.SetRequest(2, 6)), TEL_CREATORE, false);

            assertThat(p.getSet1Sq1()).isEqualTo(6);
            assertThat(p.getSet1Sq2()).isEqualTo(4);
            assertThat(p.getSet2Sq1()).isEqualTo(3);
            assertThat(p.getSet2Sq2()).isEqualTo(6);
            assertThat(p.getSet3Sq1()).isEqualTo(2);
            assertThat(p.getSet3Sq2()).isEqualTo(6);
            assertThat(r.getRisultato().getSet()).hasSize(3);
            assertThat(r.getRisultato().getSquadraUtente()).isEqualTo(1);
            assertThat(r.getRisultato().getEsito()).isEqualTo("PERSA");
        }

        @Test
        void giocatoriCampoEntranoNelleSquadre() {
            Partita p = daRegistrare(StatoPartita.CONFERMATA, FINE_APPENA_RAGGIUNTA);
            stubLock(p);

            // Posti 1 e 2 utenti, 3 e 4 campo: i due utenti giocano insieme contro i due giocatori del campo
            PartitaDTO.DettaglioResponse r = service.registraRisultato(7,
                    new PartitaDTO.RisultatoRequest(List.of(3, 4), List.of(1, 2), dueSet()), TEL_CREATORE, false);

            assertThat(p.getPartecipazioni()).extracting(Partecipazione::getSquadra).containsExactly(2, 2, 1, 1);
            assertThat(r.getPosti()).extracting(PartitaDTO.PostoResponse::getIdPosto).containsExactly(1, 2, 3, 4);
            assertThat(r.getPosti()).extracting(PartitaDTO.PostoResponse::getSquadra).containsExactly(2, 2, 1, 1);
            assertThat(r.getPosti()).extracting(PartitaDTO.PostoResponse::getTipo)
                    .containsExactly("UTENTE", "UTENTE", "CAMPO", "CAMPO");
        }

        @Test
        void unRifiutoSulBodyNonScriveNiente() {
            Partita p = daRegistrare(StatoPartita.CONFERMATA, FINE_APPENA_RAGGIUNTA);
            stubLock(p);

            assertThatThrownBy(() -> service.registraRisultato(7,
                    conSet(new PartitaDTO.SetRequest(6, 4), new PartitaDTO.SetRequest(4, 6)), TEL_CREATORE, false))
                    .isInstanceOf(RichiestaNonValidaException.class);
            assertNienteScritto(p);
        }

        // ------ Punti ------

        @Test
        void assegnaIPuntiDopoAverScrittoIlRisultatoELiMostraInRisposta() {
            Partita p = daRegistrare(StatoPartita.CONFERMATA, FINE_APPENA_RAGGIUNTA);
            stubLock(p);
            // Il calcolo legge vincitore e squadre dalla partita: devono essere già scritti quando parte
            doAnswer(invocazione -> {
                assertThat(p.getStato()).isEqualTo(StatoPartita.GIOCATA);
                assertThat(RegoleRisultatoPartita.squadraVincitrice(p)).isEqualTo(RegoleRisultatoPartita.SQUADRA_1);
                assertThat(p.getPartecipazioni()).extracting(Partecipazione::getSquadra).containsExactly(1, 2, 1, 2);
                p.getPartecipazioni().get(1).setPuntiAssegnati(-1);
                return null;
            }).when(punteggiService).assegnaPunti(p);

            PartitaDTO.DettaglioResponse r = service.registraRisultato(7, duePerDue(), TEL_2, false);

            verify(punteggiService).assegnaPunti(p);
            assertThat(r.getRisultato().getPuntiAssegnati()).isEqualTo(-1);
        }

        @Test
        void unErroreNelCalcoloDeiPuntiArrivaAlChiamante() {
            // L'eccezione deve uscire dal metodo @Transactional: è questo che annulla anche il risultato
            Partita p = daRegistrare(StatoPartita.CONFERMATA, FINE_APPENA_RAGGIUNTA);
            stubLock(p);
            IllegalStateException errore = new IllegalStateException("errore a metà");
            doThrow(errore).when(punteggiService).assegnaPunti(p);

            assertThatThrownBy(() -> service.registraRisultato(7, duePerDue(), TEL_CREATORE, false)).isSameAs(errore);
        }

        @Test
        void unaPartitaGiaGiocataNonRiceveAltriPunti() {
            // Le partite già giocate, con i punti già assegnati, non vanno mai ricalcolate
            Partita p = daRegistrare(StatoPartita.GIOCATA, FINE_APPENA_RAGGIUNTA);
            p.getPartecipazioni().get(0).setPuntiAssegnati(3);
            stubLock(p);

            assertThatThrownBy(() -> service.registraRisultato(7, duePerDue(), TEL_CREATORE, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage(GIA_REGISTRATO);
            verifyNoInteractions(punteggiService);
            assertThat(p.getPartecipazioni().get(0).getPuntiAssegnati()).isEqualTo(3);
        }

        @Test
        void unRifiutoSulBodyNonAssegnaPunti() {
            stubLock(daRegistrare(StatoPartita.CONFERMATA, FINE_APPENA_RAGGIUNTA));

            assertThatThrownBy(() -> service.registraRisultato(7, new PartitaDTO.RisultatoRequest(), TEL_CREATORE, false))
                    .isInstanceOf(RichiestaNonValidaException.class);
            verifyNoInteractions(punteggiService);
        }

        private void assertRichiestaNonValida(PartitaDTO.RisultatoRequest request, String messaggio) {
            Partita p = daRegistrare(StatoPartita.CONFERMATA, FINE_APPENA_RAGGIUNTA);
            stubLock(p);

            assertThatThrownBy(() -> service.registraRisultato(7, request, TEL_CREATORE, false))
                    .isInstanceOf(RichiestaNonValidaException.class)
                    .hasMessage(messaggio);
            assertNienteScritto(p);
        }

        private void assertNienteScritto(Partita p) {
            assertThat(p.getStato()).isEqualTo(StatoPartita.CONFERMATA);
            assertThat(p.getSet1Sq1()).isNull();
            assertThat(p.getRegistrante()).isNull();
            assertThat(p.getDataRisultato()).isNull();
            assertThat(p.getPartecipazioni()).extracting(Partecipazione::getSquadra).containsOnlyNulls();
        }

        private PartitaDTO.RisultatoRequest conSet(PartitaDTO.SetRequest... set) {
            return new PartitaDTO.RisultatoRequest(List.of(1, 3), List.of(2, 4), Arrays.asList(set));
        }

        // Campo aperto dalle 18 alle 02: lo slot delle 00:30 appartiene alla parte dopo mezzanotte
        private Partita dopoMezzanotte(LocalDate serata) {
            Partita p = daRegistrare(StatoPartita.CONFERMATA, FINE_APPENA_RAGGIUNTA);
            p.setCampo(new Campo(2, "Campo 2", true, LocalTime.of(18, 0), LocalTime.of(2, 0)));
            p.setDataPartita(serata);
            p.setOraInizio(LocalTime.of(0, 30));
            p.setOraFine(LocalTime.of(2, 0));
            return p;
        }
    }

    // ============================================
    // Coerenza tra puoRegistrareRisultato e registraRisultato
    // ============================================
    @Nested
    class CoerenzaRisultato {

        @Test
        void puoRegistrareRisultatoCoincideConRegistraRisultato() {
            List<LocalDateTime> fini = List.of(ADESSO.plusSeconds(1), ADESSO, ADESSO.minusHours(24),
                    ADESSO.minusHours(24).minusSeconds(1));
            lenient().when(utenteRepository.findById(TEL_ESTRANEO)).thenReturn(Optional.of(
                    new Utente(TEL_ESTRANEO, "Anna", "Bianchi", "x", LocalDate.of(1990, 1, 1), false, 2.5)));

            int riuscite = 0;
            for (StatoPartita stato : StatoPartita.values()) {
                for (LocalDateTime fine : fini) {
                    for (String telefono : List.of(TEL_CREATORE, TEL_2, TEL_ESTRANEO)) {
                        for (boolean conSet : List.of(false, true)) {
                            stubPartita(scenario(stato, fine, conSet));
                            boolean puo = puoRegistrare(telefono);

                            stubLock(scenario(stato, fine, conSet));
                            boolean riuscita = riesce(() -> service.registraRisultato(7, duePerDue(), telefono, false));

                            assertThat(riuscita).as("%s, fine %s, %s, con set %s", stato, fine, telefono, conSet)
                                    .isEqualTo(puo);
                            riuscite += riuscita ? 1 : 0;
                        }
                    }
                }
            }
            // Solo confermata, senza set, ai due estremi aperti della finestra, per i due utenti
            assertThat(riuscite).isEqualTo(4);
        }

        private boolean puoRegistrare(String telefono) {
            try {
                return service.getDettaglio(7, telefono, false).getAzioni().getPuoRegistrareRisultato();
            } catch (ResourceNotFoundException ex) {
                return false;
            }
        }

        private Partita scenario(StatoPartita stato, LocalDateTime fine, boolean conSet) {
            Partita p = daRegistrare(stato, fine);
            if (conSet) {
                p.setSet1Sq1(6);
                p.setSet1Sq2(4);
                p.setSet2Sq1(6);
                p.setSet2Sq2(3);
            }
            return p;
        }

        private boolean riesce(Runnable azione) {
            try {
                azione.run();
                return true;
            } catch (AccessoNegatoException | BusinessRuleException | ResourceNotFoundException ex) {
                return false;
            }
        }
    }

    // ============================================
    // Dettaglio con il risultato
    // ============================================
    @Nested
    class DettaglioConRisultato {

        @Test
        void senzaRisultatoIlCampoENullEIPostiHannoIdESquadraNull() {
            stubPartita(daRegistrare(StatoPartita.CONFERMATA, ADESSO.plusHours(2)));

            PartitaDTO.DettaglioResponse r = service.getDettaglio(7, TEL_CREATORE, false);

            assertThat(r.getRisultato()).isNull();
            assertThat(r.getPosti()).extracting(PartitaDTO.PostoResponse::getIdPosto).containsExactly(1, 2, 3, 4);
            assertThat(r.getPosti()).extracting(PartitaDTO.PostoResponse::getSquadra).containsOnlyNulls();
        }

        @Test
        void vistoDalleDueSquadreCambiaEsitoESquadra() {
            stubPartita(giocata());

            PartitaDTO.RisultatoResponse delPrimo = service.getDettaglio(7, TEL_CREATORE, false).getRisultato();
            PartitaDTO.RisultatoResponse delSecondo = service.getDettaglio(7, TEL_2, false).getRisultato();

            assertThat(delPrimo.getSquadraUtente()).isEqualTo(1);
            assertThat(delPrimo.getEsito()).isEqualTo("VINTA");
            assertThat(delPrimo.getPuntiAssegnati()).isEqualTo(12);
            assertThat(delSecondo.getSquadraUtente()).isEqualTo(2);
            assertThat(delSecondo.getEsito()).isEqualTo("PERSA");
            assertThat(delSecondo.getPuntiAssegnati()).isEqualTo(-8);

            // I set restano sempre nell'ordine squadra 1 - squadra 2
            for (PartitaDTO.RisultatoResponse r : List.of(delPrimo, delSecondo)) {
                assertThat(r.getSet()).hasSize(3);
                assertThat(r.getSet().get(1).getSquadra1()).isEqualTo(3);
                assertThat(r.getSet().get(1).getSquadra2()).isEqualTo(6);
            }
        }

        @Test
        void perLAdminSquadraEdEsitoSonoNull() {
            stubPartita(giocata());

            PartitaDTO.DettaglioResponse r = service.getDettaglio(7, "3330000099", true);

            assertThat(r.getRisultato().getSet()).hasSize(3);
            assertThat(r.getRisultato().getSquadraUtente()).isNull();
            assertThat(r.getRisultato().getEsito()).isNull();
            assertThat(r.getRisultato().getPuntiAssegnati()).isNull();
            assertTutteFalse(r.getAzioni());
        }

        @Test
        void perLAdminConUnPostoNellaPartitaSquadraEdEsitoSonoComunqueNull() {
            stubPartita(giocata());

            PartitaDTO.RisultatoResponse r = service.getDettaglio(7, TEL_CREATORE, true).getRisultato();

            assertThat(r.getSquadraUtente()).isNull();
            assertThat(r.getEsito()).isNull();
            assertThat(r.getPuntiAssegnati()).isNull();
        }

        @Test
        void perChiNonGiocaSquadraEdEsitoSonoNull() {
            stubPartita(giocata());
            stubEstraneo(2.5);

            PartitaDTO.RisultatoResponse r = service.getDettaglio(7, TEL_ESTRANEO, false).getRisultato();

            assertThat(r.getSet()).hasSize(3);
            assertThat(r.getSquadraUtente()).isNull();
            assertThat(r.getEsito()).isNull();
            assertThat(r.getPuntiAssegnati()).isNull();
        }

        @Test
        void partecipanteSenzaSquadraHaSquadraEdEsitoNull() {
            Partita p = giocata();
            p.getPartecipazioni().get(0).setSquadra(null);
            stubPartita(p);

            PartitaDTO.RisultatoResponse r = service.getDettaglio(7, TEL_CREATORE, false).getRisultato();

            assertThat(r.getSquadraUtente()).isNull();
            assertThat(r.getEsito()).isNull();
        }

        // Finita 6-4 3-6 6-2: vince la squadra 1. Posti 1 e 3 in squadra 1, posti 2 e 4 in squadra 2
        private Partita giocata() {
            Partita p = daRegistrare(StatoPartita.GIOCATA, ADESSO.minusHours(2));
            p.setSet1Sq1(6);
            p.setSet1Sq2(4);
            p.setSet2Sq1(3);
            p.setSet2Sq2(6);
            p.setSet3Sq1(6);
            p.setSet3Sq2(2);
            List<Partecipazione> posti = p.getPartecipazioni();
            posti.get(0).setSquadra(1);
            posti.get(0).setPuntiAssegnati(12);
            posti.get(1).setSquadra(2);
            posti.get(1).setPuntiAssegnati(-8);
            posti.get(2).setSquadra(1);
            posti.get(3).setSquadra(2);
            return p;
        }
    }

    // Pubblica con 4 posti: 1 creatore, 2 secondo utente, 3 e 4 giocatori campo. Il parametro è la fine,
    // con la durata di 90 minuti di partita(...)
    private Partita daRegistrare(StatoPartita stato, LocalDateTime fine) {
        Partita p = partita(stato, TipoPartita.PUBBLICA, fine.minusMinutes(90));
        aggiungi(p, 2, new Partecipazione(TipoPartecipazione.UTENTE, secondo, false, true));
        aggiungi(p, 3, new Partecipazione(TipoPartecipazione.CAMPO, null, false, false));
        aggiungi(p, 4, new Partecipazione(TipoPartecipazione.CAMPO, null, false, false));
        return p;
    }

    // 6-4 6-3 con i posti 1 e 3 contro i posti 2 e 4
    private static PartitaDTO.RisultatoRequest duePerDue() {
        return new PartitaDTO.RisultatoRequest(List.of(1, 3), List.of(2, 4), dueSet());
    }

    private static List<PartitaDTO.SetRequest> dueSet() {
        return List.of(new PartitaDTO.SetRequest(6, 4), new PartitaDTO.SetRequest(6, 3));
    }

    private void stubLock(Partita p) {
        when(partitaRepository.findByIdPerAggiornamento(p.getId())).thenReturn(Optional.of(p));
    }

    // ============================================
    // Creatore admin
    // ============================================
    // L'admin ha tolto il creatore: non ha un posto, e la partita resta ai suoi partecipanti
    @Nested
    class CreatoreAdmin {

        private final Utente admin = new Utente("3330000100", "Ada", "Admin", "x", LocalDate.of(1990, 1, 1), true, 1.0);

        @Test
        void dettaglioPubblicaVisibileATuttiILivelli() {
            stubPartita(conCreatoreAdmin(StatoPartita.CREATA, TipoPartita.PUBBLICA));
            Utente lontano = stubEstraneo(5.0);

            PartitaDTO.DettaglioResponse r = service.getDettaglio(7, TEL_ESTRANEO, false);
            assertThat(r.getTelefonoCreatore()).isEqualTo("3330000100");
            assertTutteFalse(r.getAzioni());

            lontano.setLivello(0.0);
            assertThat(service.getDettaglio(7, TEL_ESTRANEO, false).getId()).isEqualTo(7);
        }

        @Test
        void dettaglioPrivataResta404PerChiNonGioca() {
            stubPartita(conCreatoreAdmin(StatoPartita.CREATA, TipoPartita.PRIVATA));

            assertThatThrownBy(() -> service.getDettaglio(7, TEL_ESTRANEO, false))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Partita non trovata con id 7");
        }

        @Test
        void dettaglioAnnullataResta404PerChiNonGioca() {
            stubPartita(conCreatoreAdmin(StatoPartita.ANNULLATA, TipoPartita.PUBBLICA));

            assertThatThrownBy(() -> service.getDettaglio(7, TEL_ESTRANEO, false))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void bachecaATuttiILivelliConLivelloCreatoreNull() {
            stubEstraneo(5.0);
            when(partitaRepository.findByTipoAndStatoDaData(TipoPartita.PUBBLICA, StatoPartita.CREATA,
                    ADESSO.toLocalDate()))
                    .thenReturn(List.of(conCreatoreAdmin(StatoPartita.CREATA, TipoPartita.PUBBLICA)));

            List<PartitaDTO.BachecaResponse> r = service.getBachecaPubbliche(TEL_ESTRANEO, false);

            assertThat(r).hasSize(1);
            assertThat(r.get(0).getLivelloCreatore()).isNull();
            assertThat(r.get(0).getNomeCreatore()).isEqualTo("Ada");
            // Solo il partecipante occupa un posto, non l'admin
            assertThat(r.get(0).getPostiLiberi()).isEqualTo(3);
        }

        @Test
        void entraDaUnLivelloLontano() {
            Partita p = conCreatoreAdmin(StatoPartita.CREATA, TipoPartita.PUBBLICA);
            stubLock(p);
            Utente lontano = stubEstraneo(5.0);

            PartitaDTO.DettaglioResponse r = service.entra(7, TEL_ESTRANEO, false);

            assertThat(p.getPartecipazioni()).extracting(Partecipazione::getUtente).containsExactly(secondo, lontano);
            assertThat(r.getAzioni().getPuoUscire()).isTrue();
            assertThat(r.getAzioni().getPuoInvitare()).isFalse();
            assertThat(r.getAzioni().getPuoEliminare()).isFalse();
        }

        @Test
        void partecipantePuoSoloUscireEIPostiRestanoInOrdine() {
            Partita p = conCreatoreAdmin(StatoPartita.CREATA, TipoPartita.PRIVATA);
            aggiungi(p, 3, new Partecipazione(TipoPartecipazione.CAMPO, null, false, false));
            stubPartita(p);

            PartitaDTO.DettaglioResponse r = service.getDettaglio(7, TEL_2, false);

            assertThat(r.getAzioni().getPuoUscire()).isTrue();
            assertThat(r.getAzioni().getPuoInvitare()).isFalse();
            assertThat(r.getAzioni().getPuoEliminare()).isFalse();
            assertThat(r.getTelefonoCreatore()).isEqualTo("3330000100");
            assertThat(r.getPosti()).extracting(PartitaDTO.PostoResponse::getIdPosto).containsExactly(2, 3);
        }

        @Test
        void esciDellUltimoUtenteAnnullaLaPartita() {
            Partita p = conCreatoreAdmin(StatoPartita.CREATA, TipoPartita.PUBBLICA);
            stubLock(p);

            service.esci(7, TEL_2, false);

            assertThat(p.getPartecipazioni()).isEmpty();
            assertThat(p.getStato()).isEqualTo(StatoPartita.ANNULLATA);
        }

        @Test
        void esciDellUltimoUtenteAnnullaAncheConPostiCampo() {
            Partita p = conCreatoreAdmin(StatoPartita.CREATA, TipoPartita.PUBBLICA);
            aggiungi(p, 3, new Partecipazione(TipoPartecipazione.CAMPO, null, false, false));
            stubLock(p);

            service.esci(7, TEL_2, false);

            assertThat(p.getPartecipazioni()).extracting(Partecipazione::getId).containsExactly(3);
            assertThat(p.getStato()).isEqualTo(StatoPartita.ANNULLATA);
        }

        @Test
        void esciConUnAltroUtenteLasciaLoStato() {
            Partita p = conCreatoreAdmin(StatoPartita.CREATA, TipoPartita.PUBBLICA);
            aggiungi(p, 3, new Partecipazione(TipoPartecipazione.UTENTE, creatore, false, false));
            stubLock(p);

            service.esci(7, TEL_2, false);

            assertThat(p.getPartecipazioni()).hasSize(1);
            assertThat(p.getStato()).isEqualTo(StatoPartita.CREATA);
        }

        @Test
        void nessunoEliminaLaPartita() {
            Partita p = conCreatoreAdmin(StatoPartita.CREATA, TipoPartita.PUBBLICA);
            stubLock(p);

            assertThatThrownBy(() -> service.elimina(7, TEL_2, false))
                    .isInstanceOf(AccessoNegatoException.class)
                    .hasMessage("Solo il creatore può eliminare la partita");
            assertThat(p.getStato()).isEqualTo(StatoPartita.CREATA);
        }

        @Test
        void nessunoInvita() {
            Partita p = conCreatoreAdmin(StatoPartita.CREATA, TipoPartita.PUBBLICA);
            stubLock(p);

            assertThatThrownBy(() -> service.invita(7, TEL_ESTRANEO, TEL_2, false))
                    .isInstanceOf(AccessoNegatoException.class)
                    .hasMessage("Solo il creatore può invitare");
        }

        @Test
        void nessunoCercaGliInvitabili() {
            stubPartita(conCreatoreAdmin(StatoPartita.CREATA, TipoPartita.PUBBLICA));

            assertThatThrownBy(() -> service.getInvitabili(7, null, TEL_2, false))
                    .isInstanceOf(AccessoNegatoException.class)
                    .hasMessage("Solo il creatore può invitare");
        }

        @Test
        void lAdminCreatoreNonInvitaEHaAzioniTutteFalse() {
            stubPartita(conCreatoreAdmin(StatoPartita.CREATA, TipoPartita.PUBBLICA));

            assertTutteFalse(service.getDettaglio(7, "3330000100", true).getAzioni());
        }

        // Tre giorni all'inizio, un solo partecipante: il secondo utente, con il posto 2
        private Partita conCreatoreAdmin(StatoPartita stato, TipoPartita tipo) {
            Partita p = partita(stato, tipo, ADESSO.plusDays(3));
            p.rimuoviPartecipazione(p.getPartecipazioni().get(0));
            p.setCreatore(admin);
            aggiungi(p, 2, new Partecipazione(TipoPartecipazione.UTENTE, secondo, false, false));
            return p;
        }
    }

    // ============================================
    // Helper
    // ============================================
    private Partita partita(StatoPartita stato, TipoPartita tipo, LocalDateTime inizio) {
        Partita p = new Partita();
        p.setId(7);
        p.setCampo(campoDiurno);
        p.setCreatore(creatore);
        p.setDataPartita(inizio.toLocalDate());
        p.setOraInizio(inizio.toLocalTime());
        p.setOraFine(inizio.toLocalTime().plusMinutes(90));
        p.setStato(stato);
        p.setTipo(tipo);
        aggiungi(p, 1, new Partecipazione(TipoPartecipazione.UTENTE, creatore, false, false));
        return p;
    }

    private void aggiungi(Partita p, int id, Partecipazione pa) {
        pa.setId(id);
        p.aggiungiPartecipazione(pa);
    }

    private void stubPartita(Partita p) {
        when(partitaRepository.findById(p.getId())).thenReturn(Optional.of(p));
    }

    private static void assertTutteFalse(PartitaDTO.AzioniResponse a) {
        assertThat(a.getPuoInvitare()).isFalse();
        assertThat(a.getPuoEliminare()).isFalse();
        assertThat(a.getPuoUscire()).isFalse();
        assertThat(a.getPuoConfermare()).isFalse();
        assertThat(a.getPuoRegistrareRisultato()).isFalse();
    }

    private Utente stubEstraneo(double livello) {
        Utente estraneo = new Utente(TEL_ESTRANEO, "Anna", "Bianchi", "x", LocalDate.of(1990, 1, 1), false, livello);
        when(utenteRepository.findById(TEL_ESTRANEO)).thenReturn(Optional.of(estraneo));
        return estraneo;
    }
}
