package com.padelbooking.api.service;

import com.padelbooking.api.dto.PartitaDTO;
import com.padelbooking.api.exception.AccessoNegatoException;
import com.padelbooking.api.exception.BusinessRuleException;
import com.padelbooking.api.exception.ResourceNotFoundException;
import com.padelbooking.api.model.Campo;
import com.padelbooking.api.model.Partecipazione;
import com.padelbooking.api.model.Partita;
import com.padelbooking.api.model.StatoPartita;
import com.padelbooking.api.model.TipoPartecipazione;
import com.padelbooking.api.model.TipoPartita;
import com.padelbooking.api.model.Utente;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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

    @Mock
    private PartitaRepository partitaRepository;

    @Mock
    private UtenteRepository utenteRepository;

    private PartitaService service;

    private Campo campoDiurno;
    private Utente creatore;
    private Utente secondo;

    @BeforeEach
    void setUp() {
        Clock fisso = Clock.fixed(ADESSO.atZone(ZONA).toInstant(), ZONA);
        service = new PartitaService(partitaRepository, utenteRepository, fisso);
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

        private void stubLock(Partita p) {
            when(partitaRepository.findByIdPerAggiornamento(p.getId())).thenReturn(Optional.of(p));
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
