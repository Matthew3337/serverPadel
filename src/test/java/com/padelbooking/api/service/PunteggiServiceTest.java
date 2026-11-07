package com.padelbooking.api.service;

import com.padelbooking.api.dto.PartitaDTO;
import com.padelbooking.api.model.Impostazioni;
import com.padelbooking.api.model.Partecipazione;
import com.padelbooking.api.model.Partita;
import com.padelbooking.api.model.StatoPartita;
import com.padelbooking.api.model.TipoPartecipazione;
import com.padelbooking.api.model.Utente;
import com.padelbooking.api.repository.ImpostazioniRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PessimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PunteggiServiceTest {

    // La squadra 1 vince sempre: 6-4 6-3
    private static final int VINCENTI = 1;
    private static final int PERDENTI = 2;

    @Mock
    private ImpostazioniRepository impostazioniRepository;

    @Mock
    private EntityManager entityManager;

    private PunteggiService service;
    private Partita partita;
    private int prossimoIdPosto;

    @BeforeEach
    void setUp() {
        service = new PunteggiService(impostazioniRepository, entityManager);
        partita = new Partita();
        partita.setId(7);
        partita.setStato(StatoPartita.GIOCATA);
        partita.setSet1Sq1(6);
        partita.setSet1Sq2(4);
        partita.setSet2Sq1(6);
        partita.setSet2Sq2(3);
        prossimoIdPosto = 1;
        livelloCampo(1.0);
    }

    // ============================================
    // Vittoria e sconfitta
    // ============================================
    @Nested
    class Variazioni {

        @Test
        void vittoriaControMediaPerdentiMaggioreVale3() {
            Partecipazione v1 = utente(VINCENTI, "01", 1.0, 5);
            Partecipazione v2 = utente(VINCENTI, "02", 2.0, 0);
            utente(PERDENTI, "03", 2.0, 5);
            utente(PERDENTI, "04", 2.0, 5);

            service.assegnaPunti(partita);

            assertVariazione(v1, 3, 8);
            assertVariazione(v2, 3, 3);
        }

        @Test
        void vittoriaConMedieUgualiVale3AncheConLivelliDiversi() {
            // Medie entrambe 2,5 con coppie diverse: conta la media, non i singoli livelli
            Partecipazione v1 = utente(VINCENTI, "01", 1.0, 0);
            Partecipazione v2 = utente(VINCENTI, "02", 4.0, 0);
            utente(PERDENTI, "03", 2.5, 5);
            utente(PERDENTI, "04", 2.5, 5);

            service.assegnaPunti(partita);

            assertVariazione(v1, 3, 3);
            assertVariazione(v2, 3, 3);
        }

        @Test
        void vittoriaControMediaPerdentiMinoreVale1() {
            // Differenza di appena 0,25 sulla media: il confronto non deve arrotondare
            Partecipazione v1 = utente(VINCENTI, "01", 2.5, 4);
            Partecipazione v2 = utente(VINCENTI, "02", 2.0, 0);
            utente(PERDENTI, "03", 2.0, 5);
            utente(PERDENTI, "04", 2.0, 5);

            service.assegnaPunti(partita);

            assertVariazione(v1, 1, 5);
            assertVariazione(v2, 1, 1);
        }

        @Test
        void sconfittaVale1InMenoQualunqueSiaIlLivello() {
            utente(VINCENTI, "01", 5.0, 0);
            utente(VINCENTI, "02", 5.0, 0);
            Partecipazione p1 = utente(PERDENTI, "03", 1.0, 5);
            Partecipazione p2 = utente(PERDENTI, "04", 1.0, 1);

            service.assegnaPunti(partita);

            assertVariazione(p1, -1, 4);
            // Da 1 a 0: la variazione è ancora -1
            assertVariazione(p2, -1, 0);
        }

        @Test
        void perdenteA0PuntiHaVariazione0() {
            utente(VINCENTI, "01", 2.0, 0);
            utente(VINCENTI, "02", 2.0, 0);
            Partecipazione p1 = utente(PERDENTI, "03", 2.0, 0);
            Partecipazione p2 = utente(PERDENTI, "04", 2.0, 3);

            service.assegnaPunti(partita);

            assertVariazione(p1, 0, 0);
            assertVariazione(p2, -1, 2);
        }

        @Test
        void vinceLaSquadra2SeHaVintoLeiISet() {
            partita.setSet1Sq1(4);
            partita.setSet1Sq2(6);
            partita.setSet2Sq1(6);
            partita.setSet2Sq2(4);
            partita.setSet3Sq1(2);
            partita.setSet3Sq2(6);
            Partecipazione sq1 = utente(1, "01", 2.0, 2);
            utente(1, "02", 2.0, 2);
            Partecipazione sq2 = utente(2, "03", 2.0, 2);
            utente(2, "04", 2.0, 2);

            service.assegnaPunti(partita);

            assertVariazione(sq1, -1, 1);
            assertVariazione(sq2, 3, 5);
        }

        @Test
        void partitaDeiDatiDiTestVinceIl2Piu1ControIl2Piu2ConGiocatoreCampo() {
            // La partita del 25/10 in docs/dati-di-test.sql, 2-6 4-6: vincono i livelli 2 + 1 (media 1,5)
            // contro un utente di livello 2 a 0 punti e un giocatore campo di livello 2 (media 2)
            livelloCampo(2.0);
            partita.setSet1Sq1(2);
            partita.setSet1Sq2(6);
            partita.setSet2Sq1(4);
            partita.setSet2Sq2(6);
            Partecipazione v1 = utente(2, "08", 2.0, 0);
            Partecipazione v2 = utente(2, "07", 1.0, 0);
            Partecipazione perdente = utente(1, "42", 2.0, 0);
            Partecipazione campo = campo(1);

            service.assegnaPunti(partita);

            assertVariazione(v1, 3, 3);
            assertVariazione(v2, 3, 3);
            assertVariazione(perdente, 0, 0);
            assertThat(campo.getPuntiAssegnati()).isNull();
        }

        @Test
        void senzaVincitoreNonAssegnaNulla() {
            partita.setSet2Sq1(3);
            partita.setSet2Sq2(6);
            Partecipazione v1 = utente(VINCENTI, "01", 2.0, 2);

            assertThatThrownBy(() -> service.assegnaPunti(partita)).isInstanceOf(IllegalStateException.class);

            assertThat(v1.getUtente().getPunteggio()).isEqualTo(2);
            assertThat(v1.getPuntiAssegnati()).isNull();
        }
    }

    // ============================================
    // Giocatori campo
    // ============================================
    @Nested
    class GiocatoriCampo {

        @Test
        void unoNeiVincitoriUsaIlLivelloDelleImpostazioniESenzaPunti() {
            // Vincitori (2 + 4) / 2 = 3 contro perdenti 3: medie uguali, quindi +3
            livelloCampo(4.0);
            Partecipazione campo = campo(VINCENTI);
            Partecipazione v = utente(VINCENTI, "01", 2.0, 0);
            utente(PERDENTI, "02", 3.0, 5);
            utente(PERDENTI, "03", 3.0, 5);

            service.assegnaPunti(partita);

            assertVariazione(v, 3, 3);
            assertThat(campo.getPuntiAssegnati()).isNull();
        }

        @Test
        void ilLivelloDelleImpostazioniCambiaIlRisultatoDellaMedia() {
            // Vincitori (2 + 5) / 2 = 3,5 contro perdenti 3: la media dei perdenti è minore
            livelloCampo(5.0);
            campo(VINCENTI);
            Partecipazione v = utente(VINCENTI, "01", 2.0, 0);
            utente(PERDENTI, "02", 3.0, 5);
            utente(PERDENTI, "03", 3.0, 5);

            service.assegnaPunti(partita);

            assertVariazione(v, 1, 1);
        }

        @Test
        void unoNeiPerdentiContaNellaLoroMedia() {
            // Perdenti (5 + 3) / 2 = 4 contro vincitori 3,5
            livelloCampo(5.0);
            Partecipazione v = utente(VINCENTI, "01", 3.5, 0);
            utente(VINCENTI, "02", 3.5, 0);
            Partecipazione campo = campo(PERDENTI);
            Partecipazione p = utente(PERDENTI, "03", 3.0, 5);

            service.assegnaPunti(partita);

            assertVariazione(v, 3, 3);
            assertVariazione(p, -1, 4);
            assertThat(campo.getPuntiAssegnati()).isNull();
        }

        @Test
        void dueGiocatoriCampoNellaStessaSquadra() {
            // Utenti insieme a livello 2 contro due giocatori campo a livello 1
            livelloCampo(1.0);
            Partecipazione v1 = utente(VINCENTI, "01", 2.0, 0);
            Partecipazione v2 = utente(VINCENTI, "02", 2.0, 0);
            Partecipazione c1 = campo(PERDENTI);
            Partecipazione c2 = campo(PERDENTI);

            service.assegnaPunti(partita);

            assertVariazione(v1, 1, 1);
            assertVariazione(v2, 1, 1);
            assertThat(c1.getPuntiAssegnati()).isNull();
            assertThat(c2.getPuntiAssegnati()).isNull();
        }

        @Test
        void dueGiocatoriCampoDivisiTraLeSquadre() {
            livelloCampo(3.0);
            Partecipazione c1 = campo(VINCENTI);
            Partecipazione v = utente(VINCENTI, "01", 1.0, 0);
            Partecipazione c2 = campo(PERDENTI);
            Partecipazione p = utente(PERDENTI, "02", 1.0, 2);

            service.assegnaPunti(partita);

            assertVariazione(v, 3, 3);
            assertVariazione(p, -1, 1);
            assertThat(c1.getPuntiAssegnati()).isNull();
            assertThat(c2.getPuntiAssegnati()).isNull();
        }

        @Test
        void treGiocatoriCampoConUtenteVincitore() {
            // Vincitori (5 + 1) / 2 = 3 contro perdenti 1
            livelloCampo(1.0);
            Partecipazione v = utente(VINCENTI, "01", 5.0, 0);
            campo(VINCENTI);
            campo(PERDENTI);
            campo(PERDENTI);

            service.assegnaPunti(partita);

            assertVariazione(v, 1, 1);
            verify(entityManager, times(1)).refresh(any(), any(LockModeType.class));
        }

        @Test
        void treGiocatoriCampoConUtentePerdente() {
            livelloCampo(1.0);
            campo(VINCENTI);
            campo(VINCENTI);
            Partecipazione p = utente(PERDENTI, "01", 5.0, 0);
            campo(PERDENTI);

            service.assegnaPunti(partita);

            assertVariazione(p, 0, 0);
        }

        @Test
        void senzaRigaNelleImpostazioniNonAssegnaNulla() {
            when(impostazioniRepository.findById(1)).thenReturn(Optional.empty());
            Partecipazione v = utente(VINCENTI, "01", 2.0, 2);
            campo(VINCENTI);

            assertThatThrownBy(() -> service.assegnaPunti(partita)).isInstanceOf(IllegalStateException.class);

            assertThat(v.getUtente().getPunteggio()).isEqualTo(2);
            assertThat(v.getPuntiAssegnati()).isNull();
        }
    }

    // ============================================
    // Lock e atomicità
    // ============================================
    @Nested
    class LockEAtomicita {

        @Test
        void lockInScritturaInOrdineDiTelefonoInveceCheDiSquadra() {
            Partecipazione d = utente(VINCENTI, "04", 2.0, 0);
            Partecipazione a = utente(VINCENTI, "01", 2.0, 0);
            Partecipazione c = utente(PERDENTI, "03", 2.0, 0);
            Partecipazione b = utente(PERDENTI, "02", 2.0, 0);

            service.assegnaPunti(partita);

            InOrder ordine = inOrder(entityManager);
            ordine.verify(entityManager).refresh(a.getUtente(), LockModeType.PESSIMISTIC_WRITE);
            ordine.verify(entityManager).refresh(b.getUtente(), LockModeType.PESSIMISTIC_WRITE);
            ordine.verify(entityManager).refresh(c.getUtente(), LockModeType.PESSIMISTIC_WRITE);
            ordine.verify(entityManager).refresh(d.getUtente(), LockModeType.PESSIMISTIC_WRITE);
        }

        @Test
        void usaIValoriLettiConIlLockENonQuelliGiaInSessione() {
            // Simula chi ha appena registrato un'altra partita con lo stesso giocatore: la refresh porta il
            // punteggio aggiornato (10) e il livello attuale, e il calcolo deve partire da questi
            Partecipazione v = utente(VINCENTI, "01", 2.0, 5);
            utente(VINCENTI, "02", 2.0, 0);
            utente(PERDENTI, "03", 2.0, 0);
            utente(PERDENTI, "04", 2.0, 0);
            doAnswer(invocazione -> {
                Utente aggiornato = invocazione.getArgument(0);
                if (aggiornato == v.getUtente()) {
                    aggiornato.setPunteggio(10);
                    aggiornato.setLivello(5.0);
                }
                return null;
            }).when(entityManager).refresh(any(Utente.class), any(LockModeType.class));

            service.assegnaPunti(partita);

            // Con il livello 5 la media dei vincitori (3,5) supera quella dei perdenti (2): +1
            assertVariazione(v, 1, 11);
        }

        @Test
        void seUnLockFallisceANessunoVengonoCambiatiIPunti() {
            // Il rollback vero lo fa la transazione (provato sul MySQL temporaneo). Qui si verifica che
            // il calcolo non inizi prima di avere tutti i lock e che l'eccezione arrivi al chiamante.
            utente(VINCENTI, "01", 2.0, 5);
            utente(VINCENTI, "02", 2.0, 5);
            Partecipazione c = utente(PERDENTI, "03", 2.0, 5);
            Partecipazione d = utente(PERDENTI, "04", 2.0, 5);
            PessimisticLockException errore = new PessimisticLockException("lock non ottenuto");
            doAnswer(invocazione -> {
                if (invocazione.getArgument(0) == c.getUtente()) {
                    throw errore;
                }
                return null;
            }).when(entityManager).refresh(any(Utente.class), any(LockModeType.class));

            assertThatThrownBy(() -> service.assegnaPunti(partita)).isSameAs(errore);

            verify(entityManager, never()).refresh(d.getUtente(), LockModeType.PESSIMISTIC_WRITE);
            for (Partecipazione posto : partita.getPartecipazioni()) {
                assertThat(posto.getUtente().getPunteggio()).isEqualTo(5);
                assertThat(posto.getPuntiAssegnati()).isNull();
            }
        }

        @Test
        void entrambiIMetodiRichiedonoUnaTransazioneGiaAperta() throws Exception {
            // RNF_A_7: con MANDATORY i punti si scrivono solo nella transazione del risultato, e un errore
            // qui marca il rollback anche di partita e squadre. Senza transazione il metodo fallisce.
            assertThat(propagazione("assegnaPunti")).isEqualTo(Propagation.MANDATORY);
            assertThat(propagazione("stornaPunti")).isEqualTo(Propagation.MANDATORY);
            // Chi chiama apre la transazione unica
            assertThat(PartitaService.class.getMethod("registraRisultato", Integer.class,
                            PartitaDTO.RisultatoRequest.class, String.class, boolean.class)
                    .getAnnotation(Transactional.class)).isNotNull();
        }

        private Propagation propagazione(String metodo) throws Exception {
            return PunteggiService.class.getMethod(metodo, Partita.class)
                    .getAnnotation(Transactional.class).propagation();
        }
    }

    // ============================================
    // Storno
    // ============================================
    @Nested
    class Storno {

        @Test
        void sottraeLeVariazioniEAzzeraIPunti() {
            Partecipazione vincitore = conPunti(utente(VINCENTI, "01", 2.0, 8), 3);
            Partecipazione perdente = conPunti(utente(PERDENTI, "02", 2.0, 4), -1);

            service.stornaPunti(partita);

            assertVariazione(vincitore, null, 5);
            // Togliere -1 restituisce il punto perso
            assertVariazione(perdente, null, 5);
        }

        @Test
        void nonScendeSotto0() {
            // Dopo la vittoria il giocatore ha perso altre partite: ha meno punti di quelli da togliere
            Partecipazione vincitore = conPunti(utente(VINCENTI, "01", 2.0, 1), 3);

            service.stornaPunti(partita);

            assertVariazione(vincitore, null, 0);
        }

        @Test
        void unaVariazione0NonCambiaIlPunteggioMaSiAzzera() {
            Partecipazione perdente = conPunti(utente(PERDENTI, "01", 2.0, 0), 0);

            service.stornaPunti(partita);

            assertVariazione(perdente, null, 0);
        }

        @Test
        void ignoraGiocatoriCampoEPostiSenzaPunti() {
            Partecipazione senzaPunti = utente(VINCENTI, "01", 2.0, 5);
            Partecipazione campo = campo(VINCENTI);
            Partecipazione conPunti = conPunti(utente(PERDENTI, "02", 2.0, 5), -1);

            service.stornaPunti(partita);

            assertVariazione(senzaPunti, null, 5);
            assertThat(campo.getPuntiAssegnati()).isNull();
            assertVariazione(conPunti, null, 6);
            // Lock solo sulle righe che cambiano
            verify(entityManager, never()).refresh(senzaPunti.getUtente(), LockModeType.PESSIMISTIC_WRITE);
        }

        @Test
        void chiamatoDueVolteNonTogliePuntiDueVolte() {
            Partecipazione vincitore = conPunti(utente(VINCENTI, "01", 2.0, 8), 3);

            service.stornaPunti(partita);
            service.stornaPunti(partita);

            assertVariazione(vincitore, null, 5);
        }

        @Test
        void lockInScritturaInOrdineDiTelefono() {
            Partecipazione b = conPunti(utente(VINCENTI, "02", 2.0, 8), 3);
            Partecipazione a = conPunti(utente(PERDENTI, "01", 2.0, 8), -1);

            service.stornaPunti(partita);

            InOrder ordine = inOrder(entityManager);
            ordine.verify(entityManager).refresh(a.getUtente(), LockModeType.PESSIMISTIC_WRITE);
            ordine.verify(entityManager).refresh(b.getUtente(), LockModeType.PESSIMISTIC_WRITE);
        }

        @Test
        void assegnazioneEStornoRiportanoAlPunteggioDiPartenza() {
            Partecipazione v1 = utente(VINCENTI, "01", 2.0, 2);
            Partecipazione v2 = utente(VINCENTI, "02", 2.0, 0);
            Partecipazione p1 = utente(PERDENTI, "03", 2.0, 2);
            // Da 0 a 0: la variazione memorizzata è 0, quindi lo storno non gli regala un punto (RF_GCL_11)
            Partecipazione p2 = utente(PERDENTI, "04", 2.0, 0);

            service.assegnaPunti(partita);
            service.stornaPunti(partita);

            assertVariazione(v1, null, 2);
            assertVariazione(v2, null, 0);
            assertVariazione(p1, null, 2);
            assertVariazione(p2, null, 0);
        }

        @Test
        void ricalcoloDopoLoStornoUsaILivelliAttuali() {
            // RF_GCL_13: la correzione di T11 sarà storno e nuovo calcolo
            Partecipazione v = utente(VINCENTI, "01", 2.0, 0);
            utente(VINCENTI, "02", 2.0, 0);
            utente(PERDENTI, "03", 2.0, 0);
            utente(PERDENTI, "04", 2.0, 0);

            service.assegnaPunti(partita);
            assertVariazione(v, 3, 3);

            v.getUtente().setLivello(4.0);
            service.stornaPunti(partita);
            service.assegnaPunti(partita);

            assertVariazione(v, 1, 1);
        }

        @Test
        void assegnaPuntiNonFaFlushPrimaDeiLock() {
            // Scriverebbe telefono_registrante sulla partita: il lock condiviso della chiave esterna, tenuto
            // mentre si chiede quello in scrittura, manda in deadlock due registrazioni parallele
            utente(VINCENTI, "01", 2.0, 0);
            utente(PERDENTI, "02", 2.0, 0);

            service.assegnaPunti(partita);

            verify(entityManager, never()).flush();
        }

        @Test
        void refreshDiAssegnaPuntiTrovaLoStornoGiaScritto() {
            // refresh rilegge il punteggio dal database: senza il flush prima, lo storno andrebbe perso
            Partecipazione v = conPunti(utente(VINCENTI, "01", 2.0, 8), 3);
            utente(VINCENTI, "02", 2.0, 0);
            utente(PERDENTI, "03", 2.0, 0);
            utente(PERDENTI, "04", 2.0, 0);
            int[] sulDatabase = {8};
            doAnswer(invocazione -> {
                sulDatabase[0] = v.getUtente().getPunteggio();
                return null;
            }).when(entityManager).flush();
            doAnswer(invocazione -> {
                if (invocazione.getArgument(0) == v.getUtente()) {
                    v.getUtente().setPunteggio(sulDatabase[0]);
                }
                return null;
            }).when(entityManager).refresh(any(Utente.class), any(LockModeType.class));

            service.stornaPunti(partita);
            service.assegnaPunti(partita);

            // 8 - 3 + 3, non 8 + 3
            assertVariazione(v, 3, 8);
        }

        @Test
        void conUnPostoAnonimoStornaGliAltriEAzzeraIlSuo() {
            Partecipazione vincitore = conPunti(utente(VINCENTI, "01", 2.0, 8), 3);
            Partecipazione anonimo = conPunti(utente(VINCENTI, "02", 2.0, 4), 3);
            anonimo.setUtente(null);

            service.stornaPunti(partita);

            assertVariazione(vincitore, null, 5);
            assertThat(anonimo.getPuntiAssegnati()).isNull();
        }

        private Partecipazione conPunti(Partecipazione posto, int punti) {
            posto.setPuntiAssegnati(punti);
            return posto;
        }
    }

    // T18: il posto di un utente eliminato conta come un giocatore campo e non riceve punti
    @Test
    void assegnaPuntiConUnPostoAnonimo() {
        livelloCampo(4.0);
        Partecipazione anonimo = utente(VINCENTI, "01", 1.0, 0);
        anonimo.setUtente(null);
        Partecipazione compagno = utente(VINCENTI, "02", 2.0, 10);
        Partecipazione avversario1 = utente(PERDENTI, "03", 3.0, 10);
        Partecipazione avversario2 = utente(PERDENTI, "04", 3.0, 10);

        service.assegnaPunti(partita);

        assertThat(anonimo.getPuntiAssegnati()).isNull();
        // Media vincenti (4 + 2) / 2 = 3 contro 3: vittoria contro livello maggiore o uguale
        assertVariazione(compagno, 3, 13);
        assertVariazione(avversario1, -1, 9);
        assertVariazione(avversario2, -1, 9);
    }

    // ============================================
    // Helper
    // ============================================

    private void livelloCampo(double livello) {
        Impostazioni impostazioni = new Impostazioni();
        impostazioni.setId(1);
        impostazioni.setLivelloGiocatoriCampo(livello);
        lenient().when(impostazioniRepository.findById(1)).thenReturn(Optional.of(impostazioni));
    }

    private Partecipazione utente(int squadra, String finaleTelefono, double livello, int punteggio) {
        Utente utente = new Utente("33300000" + finaleTelefono, "Nome", "Cognome", "x",
                LocalDate.of(1990, 1, 1), false, livello);
        utente.setPunteggio(punteggio);
        return posto(squadra, new Partecipazione(TipoPartecipazione.UTENTE, utente, false, true));
    }

    private Partecipazione campo(int squadra) {
        return posto(squadra, new Partecipazione(TipoPartecipazione.CAMPO, null, false, false));
    }

    private Partecipazione posto(int squadra, Partecipazione posto) {
        posto.setId(prossimoIdPosto++);
        posto.setSquadra(squadra);
        partita.aggiungiPartecipazione(posto);
        return posto;
    }

    private static void assertVariazione(Partecipazione posto, Integer variazione, int punteggio) {
        assertThat(posto.getPuntiAssegnati()).isEqualTo(variazione);
        assertThat(posto.getUtente().getPunteggio()).isEqualTo(punteggio);
    }
}
