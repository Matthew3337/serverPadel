package com.padelbooking.api.service;

import com.padelbooking.api.model.Avviso;
import com.padelbooking.api.model.Campo;
import com.padelbooking.api.model.Partecipazione;
import com.padelbooking.api.model.Partita;
import com.padelbooking.api.model.StatoPartita;
import com.padelbooking.api.model.TipoAvviso;
import com.padelbooking.api.model.TipoPartecipazione;
import com.padelbooking.api.model.TipoPartita;
import com.padelbooking.api.model.Utente;
import com.padelbooking.api.repository.AvvisoRepository;
import com.padelbooking.api.repository.PartitaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScadenzePartiteServiceTest {

    private static final ZoneId ZONA = ZoneId.of("Europe/Rome");
    // "Adesso" fisso per tutti i test
    private static final LocalDateTime ADESSO = LocalDateTime.of(2026, 11, 1, 12, 0);

    @Mock
    private PartitaRepository partitaRepository;

    @Mock
    private AvvisoRepository avvisoRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    private ScadenzePartiteService service;

    private Campo campoDiurno;
    private Utente creatore;
    private Utente secondo;

    @BeforeEach
    void setUp() {
        Clock fisso = Clock.fixed(ADESSO.atZone(ZONA).toInstant(), ZONA);
        service = new ScadenzePartiteService(partitaRepository, avvisoRepository, transactionManager, fisso);
        // Ogni partita apre una transazione: il mock restituisce uno stato valido per commit e rollback
        lenient().when(transactionManager.getTransaction(any())).thenAnswer(inv -> new SimpleTransactionStatus());
        campoDiurno = new Campo(1, "Campo 1", true, LocalTime.of(8, 0), LocalTime.of(22, 0));
        creatore = new Utente("3330000001", "Mario", "Rossi", "x", LocalDate.of(1990, 1, 1), false, 2.5);
        secondo = new Utente("3330000002", "Luigi", "Verdi", "x", LocalDate.of(1990, 1, 1), false, 3.0);
    }

    // ============================================
    // Lettura delle candidate
    // ============================================
    @Nested
    class Candidate {

        @Test
        void leggeCreateEConfermateIncompleteFinoADopodomani() {
            when(partitaRepository.findCandidateScadenze(any(), any(), any(Integer.class), any())).thenReturn(List.of());

            service.eseguiGiro();

            verify(partitaRepository).findCandidateScadenze(StatoPartita.CREATA, StatoPartita.CONFERMATA, 4,
                    LocalDate.of(2026, 11, 3));
        }

        @Test
        void primaDelleMeno24OreNonApreTransazioni() {
            Partita p = partita(1, StatoPartita.CREATA, ADESSO.plusHours(24).plusSeconds(1), 1);
            stubCandidate(p);

            service.eseguiGiro();

            verify(partitaRepository, never()).findByIdPerAggiornamento(any());
            verify(transactionManager, never()).getTransaction(any());
            assertThat(p.getStato()).isEqualTo(StatoPartita.CREATA);
        }
    }

    // ============================================
    // −24 h: avviso di partita incompleta
    // ============================================
    @Nested
    class Avviso24Ore {

        @Test
        void esattamenteA24OreConPostiLiberiCreaAvviso() {
            Partita p = partita(1, StatoPartita.CREATA, ADESSO.plusHours(24), 2);
            stubCandidate(p);
            stubLock(p);

            service.eseguiGiro();

            ArgumentCaptor<Avviso> avviso = ArgumentCaptor.forClass(Avviso.class);
            verify(avvisoRepository).save(avviso.capture());
            assertThat(avviso.getValue().getPartita()).isSameAs(p);
            assertThat(avviso.getValue().getTipo()).isEqualTo(TipoAvviso.PARTITA_INCOMPLETA);
            assertThat(avviso.getValue().getDataCreazione()).isEqualTo(ADESSO);
            assertThat(avviso.getValue().getLetto()).isFalse();
            // RF_GC_2: la partita resta creata, l'admin può ancora completarla
            assertThat(p.getStato()).isEqualTo(StatoPartita.CREATA);
            verify(transactionManager).commit(any());
        }

        @Test
        void senzaPostiLiberiNessunAvviso() {
            Partita p = partita(1, StatoPartita.CREATA, ADESSO.plusHours(20), 4);
            stubCandidate(p);
            stubLock(p);

            service.eseguiGiro();

            verify(avvisoRepository, never()).save(any());
            assertThat(p.getStato()).isEqualTo(StatoPartita.CREATA);
        }

        @Test
        void avvisoGiaEsistenteNonVieneDuplicato() {
            Partita p = partita(1, StatoPartita.CREATA, ADESSO.plusHours(20), 3);
            stubCandidate(p);
            stubLock(p);
            when(avvisoRepository.existsByPartitaIdAndTipo(1, TipoAvviso.PARTITA_INCOMPLETA)).thenReturn(true);

            service.eseguiGiro();

            verify(avvisoRepository, never()).save(any());
        }

        @Test
        void tra12OreEInizioNessunaModificaAllaCreataCompleta() {
            // −12 h: niente da salvare, la partita non confermata resta CREATA fino all'inizio (RF_GC_8)
            Partita p = partita(1, StatoPartita.CREATA, ADESSO.plusHours(6), 4);
            stubCandidate(p);
            stubLock(p);

            service.eseguiGiro();

            assertThat(p.getStato()).isEqualTo(StatoPartita.CREATA);
            verify(avvisoRepository, never()).save(any());
        }

        @Test
        void confermataIncompletaPrimaDellInizioNonCambia() {
            Partita p = partita(1, StatoPartita.CONFERMATA, ADESSO.plusHours(6), 3);
            stubCandidate(p);
            stubLock(p);

            service.eseguiGiro();

            assertThat(p.getStato()).isEqualTo(StatoPartita.CONFERMATA);
            verify(avvisoRepository, never()).save(any());
        }

        @Test
        void statoCambiatoDopoLaLetturaDelleCandidateNonCreaAvviso() {
            // Il creatore l'ha eliminata tra la lettura delle candidate e il lock
            Partita candidata = partita(1, StatoPartita.CREATA, ADESSO.plusHours(20), 2);
            Partita bloccata = partita(1, StatoPartita.ANNULLATA, ADESSO.plusHours(20), 2);
            stubCandidate(candidata);
            stubLock(bloccata);

            service.eseguiGiro();

            verify(avvisoRepository, never()).save(any());
            assertThat(bloccata.getStato()).isEqualTo(StatoPartita.ANNULLATA);
        }
    }

    // ============================================
    // Inizio: annullamento automatico
    // ============================================
    @Nested
    class Inizio {

        @Test
        void creataAllInizioDiventaAnnullataSenzaAvviso() {
            Partita p = partita(1, StatoPartita.CREATA, ADESSO, 4);
            stubCandidate(p);
            stubLock(p);

            service.eseguiGiro();

            assertThat(p.getStato()).isEqualTo(StatoPartita.ANNULLATA);
            verify(avvisoRepository, never()).save(any());
        }

        @Test
        void creataUnSecondoPrimaDellInizioNonVieneAnnullata() {
            Partita p = partita(1, StatoPartita.CREATA, ADESSO.plusSeconds(1), 4);
            stubCandidate(p);
            stubLock(p);

            service.eseguiGiro();

            assertThat(p.getStato()).isEqualTo(StatoPartita.CREATA);
        }

        @Test
        void confermataConMenoDi4PostiDiventaAnnullata() {
            Partita p = partita(1, StatoPartita.CONFERMATA, ADESSO, 3);
            stubCandidate(p);
            stubLock(p);

            service.eseguiGiro();

            assertThat(p.getStato()).isEqualTo(StatoPartita.ANNULLATA);
        }

        @Test
        void confermataCon4PostiRestaConfermata() {
            Partita p = partita(1, StatoPartita.CONFERMATA, ADESSO, 4);
            stubCandidate(p);
            stubLock(p);

            service.eseguiGiro();

            assertThat(p.getStato()).isEqualTo(StatoPartita.CONFERMATA);
        }

        @Test
        void giroRipetutoEIdempotente() {
            Partita p = partita(1, StatoPartita.CREATA, ADESSO, 2);
            stubCandidate(p);
            stubLock(p);

            service.eseguiGiro();
            service.eseguiGiro();

            assertThat(p.getStato()).isEqualTo(StatoPartita.ANNULLATA);
            verify(avvisoRepository, never()).save(any());
        }

        @Test
        void giocataNonSiToccaDopoLInizio() {
            // Una partita diventata GIOCATA tra la lettura delle candidate e il lock non si annulla
            Partita p = partita(1, StatoPartita.GIOCATA, ADESSO.minusHours(3), 4);
            p.setSet1Sq1(6);
            p.setSet1Sq2(4);
            p.setSet2Sq1(6);
            p.setSet2Sq2(3);
            stubCandidate(p);
            stubLock(p);

            service.eseguiGiro();

            assertThat(p.getStato()).isEqualTo(StatoPartita.GIOCATA);
            assertThat(p.getSet1Sq1()).isEqualTo(6);
            verify(avvisoRepository, never()).save(any());
        }

        @Test
        void giocataNonRiceveAvvisiPrimaDellInizio() {
            Partita p = partita(1, StatoPartita.GIOCATA, ADESSO.plusHours(3), 2);
            stubCandidate(p);
            stubLock(p);

            service.eseguiGiro();

            assertThat(p.getStato()).isEqualTo(StatoPartita.GIOCATA);
            verify(avvisoRepository, never()).save(any());
        }
    }

    // ============================================
    // Recupero dopo un fermo e partite dopo mezzanotte
    // ============================================
    @Nested
    class Recupero {

        @Test
        void dopoUnFermoRecuperaAnnullamentoEAvvisoMaturati() {
            // Server fermo da due giorni: la prima è iniziata ieri, la seconda ha superato le −24 h da 10 ore
            Partita iniziataIeri = partita(1, StatoPartita.CREATA, ADESSO.minusDays(2), 2);
            Partita nellaFinestra = partita(2, StatoPartita.CREATA, ADESSO.plusHours(14), 3);
            stubCandidate(iniziataIeri, nellaFinestra);
            stubLock(iniziataIeri);
            stubLock(nellaFinestra);

            service.eseguiGiro();

            assertThat(iniziataIeri.getStato()).isEqualTo(StatoPartita.ANNULLATA);
            assertThat(nellaFinestra.getStato()).isEqualTo(StatoPartita.CREATA);
            ArgumentCaptor<Avviso> avviso = ArgumentCaptor.forClass(Avviso.class);
            verify(avvisoRepository).save(avviso.capture());
            assertThat(avviso.getValue().getPartita()).isSameAs(nellaFinestra);
        }

        @Test
        void partitaDopoMezzanotteUsaInizioEffettivo() {
            // Campo 18:00-02:00, serata del 1° novembre, ore 00:30: inizia il 2 novembre alle 00:30,
            // fra 12h30. Con data_partita + ora sarebbe già iniziata e verrebbe annullata.
            Campo serale = new Campo(2, "Campo Serale", false, LocalTime.of(18, 0), LocalTime.of(2, 0));
            Partita p = partita(1, StatoPartita.CREATA, LocalDateTime.of(2026, 11, 1, 0, 30), 2);
            p.setCampo(serale);
            stubCandidate(p);
            stubLock(p);

            service.eseguiGiro();

            assertThat(p.getStato()).isEqualTo(StatoPartita.CREATA);
            verify(avvisoRepository).save(any());
        }

        @Test
        void partitaDopoMezzanotteDellaSerataSuccessivaNonEMatura() {
            // Serata del 2 novembre, ore 00:30: inizia il 3 alle 00:30, fra 36h30
            Campo serale = new Campo(2, "Campo Serale", false, LocalTime.of(18, 0), LocalTime.of(2, 0));
            Partita p = partita(1, StatoPartita.CREATA, LocalDateTime.of(2026, 11, 2, 0, 30), 2);
            p.setCampo(serale);
            stubCandidate(p);

            service.eseguiGiro();

            verify(partitaRepository, never()).findByIdPerAggiornamento(any());
        }
    }

    // ============================================
    // Errori
    // ============================================
    @Nested
    class Errori {

        @Test
        void erroreSuUnaPartitaNonBloccaLeAltre() {
            Partita guasta = partita(1, StatoPartita.CREATA, ADESSO.minusHours(1), 2);
            Partita sana = partita(2, StatoPartita.CREATA, ADESSO.minusHours(1), 2);
            stubCandidate(guasta, sana);
            when(partitaRepository.findByIdPerAggiornamento(1)).thenThrow(new IllegalStateException("guasto"));
            stubLock(sana);

            service.eseguiGiro();

            assertThat(sana.getStato()).isEqualTo(StatoPartita.ANNULLATA);
            // Una transazione per partita: la prima annullata, la seconda confermata
            verify(transactionManager, times(2)).getTransaction(any());
            verify(transactionManager).rollback(any());
            verify(transactionManager).commit(any());
        }

        @Test
        void erroreNelSalvataggioDellAvvisoNonBloccaLeAltre() {
            Partita guasta = partita(1, StatoPartita.CREATA, ADESSO.plusHours(20), 2);
            Partita sana = partita(2, StatoPartita.CREATA, ADESSO.minusHours(1), 2);
            stubCandidate(guasta, sana);
            stubLock(guasta);
            stubLock(sana);
            when(avvisoRepository.save(any())).thenThrow(new IllegalStateException("guasto"));

            service.eseguiGiro();

            assertThat(sana.getStato()).isEqualTo(StatoPartita.ANNULLATA);
            verify(transactionManager).rollback(any());
        }

        @Test
        void partitaNonPiuPresenteVieneSaltata() {
            Partita p = partita(1, StatoPartita.CREATA, ADESSO, 2);
            stubCandidate(p);
            when(partitaRepository.findByIdPerAggiornamento(1)).thenReturn(Optional.empty());

            service.eseguiGiro();

            verify(transactionManager).commit(any());
            verify(avvisoRepository, never()).save(any());
        }
    }

    // ============================================
    // Helper
    // ============================================

    // Partita con il creatore e, fino a "posti", un secondo utente e poi giocatori campo
    private Partita partita(int id, StatoPartita stato, LocalDateTime inizio, int posti) {
        Partita p = new Partita();
        p.setId(id);
        p.setCampo(campoDiurno);
        p.setCreatore(creatore);
        p.setDataPartita(inizio.toLocalDate());
        p.setOraInizio(inizio.toLocalTime());
        p.setOraFine(inizio.toLocalTime().plusMinutes(90));
        p.setStato(stato);
        p.setTipo(TipoPartita.PUBBLICA);
        aggiungi(p, id * 10 + 1, new Partecipazione(TipoPartecipazione.UTENTE, creatore, false, false));
        if (posti >= 2) {
            aggiungi(p, id * 10 + 2, new Partecipazione(TipoPartecipazione.UTENTE, secondo, false, false));
        }
        for (int i = 3; i <= posti; i++) {
            aggiungi(p, id * 10 + i, new Partecipazione(TipoPartecipazione.CAMPO, null, false, false));
        }
        return p;
    }

    private void aggiungi(Partita p, int id, Partecipazione pa) {
        pa.setId(id);
        p.aggiungiPartecipazione(pa);
    }

    private void stubCandidate(Partita... partite) {
        when(partitaRepository.findCandidateScadenze(any(), any(), any(Integer.class), any()))
                .thenReturn(List.of(partite));
    }

    private void stubLock(Partita p) {
        when(partitaRepository.findByIdPerAggiornamento(p.getId())).thenReturn(Optional.of(p));
    }
}
