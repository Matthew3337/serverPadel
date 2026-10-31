package com.padelbooking.api.service;

import com.padelbooking.api.dto.PartitaDTO;
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

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    private void stubEstraneo(double livello) {
        Utente estraneo = new Utente(TEL_ESTRANEO, "Anna", "Bianchi", "x", LocalDate.of(1990, 1, 1), false, livello);
        when(utenteRepository.findById(TEL_ESTRANEO)).thenReturn(Optional.of(estraneo));
    }
}
