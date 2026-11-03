package com.padelbooking.api.service;

import com.padelbooking.api.dto.AdminDTO;
import com.padelbooking.api.dto.PartitaDTO;
import com.padelbooking.api.exception.BusinessRuleException;
import com.padelbooking.api.exception.ResourceNotFoundException;
import com.padelbooking.api.exception.RichiestaNonValidaException;
import com.padelbooking.api.model.Avviso;
import com.padelbooking.api.model.Campo;
import com.padelbooking.api.model.Impostazioni;
import com.padelbooking.api.model.Partecipazione;
import com.padelbooking.api.model.Partita;
import com.padelbooking.api.model.StatoPartita;
import com.padelbooking.api.model.TipoPartecipazione;
import com.padelbooking.api.model.TipoPartita;
import com.padelbooking.api.model.Utente;
import com.padelbooking.api.repository.AvvisoRepository;
import com.padelbooking.api.repository.CampoRepository;
import com.padelbooking.api.repository.ImpostazioniRepository;
import com.padelbooking.api.repository.PartecipazioneRepository;
import com.padelbooking.api.repository.PartitaRepository;
import com.padelbooking.api.repository.UtenteRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// PartitaService e PunteggiService sono quelli veri: i punti e il dettaglio sono parte del contratto.
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    private static final String TEL_A = "3330000001";
    private static final String TEL_B = "3330000002";
    private static final String TEL_C = "3330000003";
    private static final String TEL_D = "3330000004";
    private static final String TEL_E = "3330000005";
    private static final String TEL_ADMIN = "3330000100";
    private static final ZoneId ZONA = ZoneId.of("Europe/Rome");
    private static final LocalDateTime ADESSO = LocalDateTime.of(2026, 11, 2, 12, 0);
    private static final LocalDate DATA = LocalDate.of(2026, 11, 5);
    private static final int ID_PARTITA = 7;

    @Mock
    private PartitaRepository partitaRepository;
    @Mock
    private PartecipazioneRepository partecipazioneRepository;
    @Mock
    private CampoRepository campoRepository;
    @Mock
    private AvvisoRepository avvisoRepository;
    @Mock
    private UtenteRepository utenteRepository;
    @Mock
    private ImpostazioniRepository impostazioniRepository;
    @Mock
    private EntityManager entityManager;

    private AdminService service;
    private Campo campo;
    private Utente admin;
    private final Map<String, Utente> utenti = new HashMap<>();
    // Il punteggio sul database: cambia solo al flush e la refresh lo riporta sull'Entity, come fa
    // Hibernate. Così un punteggio non ancora scritto va perso davvero, se qualcuno fa refresh prima.
    private final Map<String, Integer> punteggiSulDatabase = new HashMap<>();
    private int prossimoIdPosto;

    @BeforeEach
    void setUp() {
        Clock fisso = Clock.fixed(ADESSO.atZone(ZONA).toInstant(), ZONA);
        PunteggiService punteggi = new PunteggiService(impostazioniRepository, entityManager);
        PartitaService partite = new PartitaService(partitaRepository, partecipazioneRepository, utenteRepository,
                punteggi, fisso);
        service = new AdminService(partitaRepository, campoRepository, avvisoRepository, utenteRepository,
                partite, punteggi, entityManager, fisso);

        campo = new Campo(1, "Campo Centrale", true, LocalTime.of(8, 0), LocalTime.of(23, 0));
        admin = new Utente(TEL_ADMIN, "Ada", "Admin", "x", LocalDate.of(1990, 1, 1), true);
        prossimoIdPosto = 11;

        Impostazioni impostazioni = new Impostazioni();
        impostazioni.setId(1);
        impostazioni.setLivelloGiocatoriCampo(1.0);
        lenient().when(impostazioniRepository.findById(1)).thenReturn(Optional.of(impostazioni));
        lenient().when(utenteRepository.getReferenceById(TEL_ADMIN)).thenReturn(admin);
        lenient().doAnswer(invocazione -> {
            Utente utente = invocazione.getArgument(0);
            utente.setPunteggio(punteggiSulDatabase.get(utente.getTelefono()));
            return null;
        }).when(entityManager).refresh(any(Utente.class), any(LockModeType.class));
        lenient().doAnswer(invocazione -> {
            utenti.forEach((telefono, utente) -> punteggiSulDatabase.put(telefono, utente.getPunteggio()));
            return null;
        }).when(entityManager).flush();
    }

    // ============================================
    // Partite del giorno
    // ============================================
    @Nested
    class PartiteDelGiorno {

        @Test
        void unElementoPerOgniCampoAncheSenzaPartite() {
            Campo vuoto = new Campo(2, "Campo Nord", false, LocalTime.of(8, 0), LocalTime.of(22, 0));
            when(campoRepository.findAll(Sort.by("nome", "id"))).thenReturn(List.of(campo, vuoto));
            when(partitaRepository.findByDataConPartecipanti(DATA)).thenReturn(List.of());

            List<AdminDTO.CampoPartiteResponse> risposta = service.getPartiteDelGiorno(DATA);

            assertThat(risposta).extracting(AdminDTO.CampoPartiteResponse::getNomeCampo)
                    .containsExactly("Campo Centrale", "Campo Nord");
            assertThat(risposta.get(1).getIdCampo()).isEqualTo(2);
            assertThat(risposta.get(1).getPartite()).isEmpty();
        }

        @Test
        void annullateComprese_ordinePerOra_conFormatoDelContratto() {
            Partita sera = partita(StatoPartita.ANNULLATA, 18, TEL_A);
            sera.setId(9);
            sera.aggiungiPartecipazione(postoCampo());
            sera.setSet1Sq1(6);
            sera.setSet1Sq2(4);
            sera.setSet2Sq1(6);
            sera.setSet2Sq2(3);
            Partita mattina = partita(StatoPartita.CREATA, 9, TEL_B);
            mattina.setId(8);
            // Stesso slot della annullata: decide l'id
            Partita stessoSlot = partita(StatoPartita.CREATA, 18, TEL_C);
            stessoSlot.setId(10);
            when(campoRepository.findAll(Sort.by("nome", "id"))).thenReturn(List.of(campo));
            when(partitaRepository.findByDataConPartecipanti(DATA)).thenReturn(List.of(stessoSlot, sera, mattina));

            List<AdminDTO.PartitaResponse> partite = service.getPartiteDelGiorno(DATA).get(0).getPartite();

            assertThat(partite).extracting(AdminDTO.PartitaResponse::getId).containsExactly(8, 9, 10);
            AdminDTO.PartitaResponse annullata = partite.get(1);
            assertThat(annullata.getStato()).isEqualTo("annullata");
            assertThat(annullata.getTipo()).isEqualTo("PUBBLICA");
            assertThat(annullata.getOraInizio()).isEqualTo(LocalTime.of(18, 0));
            assertThat(annullata.getOraFine()).isEqualTo(LocalTime.of(19, 30));
            assertThat(annullata.getGiocatori()).containsExactly("Nome" + TEL_A + " Cognome", "Giocatore del campo");
            assertThat(annullata.getPostiLiberi()).isEqualTo(2);
            assertThat(annullata.getRisultato()).extracting(PartitaDTO.SetResponse::getSquadra1).containsExactly(6, 6);
            assertThat(annullata.getRisultato()).extracting(PartitaDTO.SetResponse::getSquadra2).containsExactly(4, 3);
            assertThat(partite.get(0).getRisultato()).isNull();
        }

        @Test
        void ordinePerInizioEffettivo_00e30DellaSerataDopoLe18() {
            Campo notturno = new Campo(2, "Campo Notturno", false, LocalTime.of(16, 0), LocalTime.of(2, 0));
            Partita dopoMezzanotte = partitaIn(notturno, DATA, LocalTime.of(0, 30));
            dopoMezzanotte.setId(1);
            Partita sera = partitaIn(notturno, DATA, LocalTime.of(18, 0));
            sera.setId(2);
            Partita tardi = partitaIn(notturno, DATA, LocalTime.of(23, 0));
            tardi.setId(3);
            when(campoRepository.findAll(Sort.by("nome", "id"))).thenReturn(List.of(notturno));
            when(partitaRepository.findByDataConPartecipanti(DATA)).thenReturn(List.of(dopoMezzanotte, tardi, sera));

            List<AdminDTO.PartitaResponse> partite = service.getPartiteDelGiorno(DATA).get(0).getPartite();

            assertThat(partite).extracting(AdminDTO.PartitaResponse::getId).containsExactly(2, 3, 1);
        }
    }

    // ============================================
    // Bacheca avvisi
    // ============================================
    @Nested
    class BachecaAvvisi {

        @Test
        void soloNonIniziateOrdinateSullInizioEffettivoConFormatoDelContratto() {
            // Un campo che chiude dopo mezzanotte: la partita alle 00:30 della serata del 2/11 è il 3/11
            Campo notturno = new Campo(3, "Notturno", false, LocalTime.of(18, 0), LocalTime.of(2, 0));
            Partita dopoMezzanotte = partitaIn(notturno, ADESSO.toLocalDate(), LocalTime.of(0, 30));
            dopoMezzanotte.setId(21);
            Partita diSera = partitaIn(notturno, ADESSO.toLocalDate(), LocalTime.of(20, 0));
            diSera.setId(22);
            diSera.aggiungiPartecipazione(postoCampo());
            Partita iniziata = partitaIn(campo, ADESSO.toLocalDate(), ADESSO.toLocalTime());
            iniziata.setId(23);
            when(avvisoRepository.findAperti(StatoPartita.ANNULLATA, ADESSO.toLocalDate().minusDays(1)))
                    .thenReturn(List.of(avviso(1, dopoMezzanotte), avviso(2, iniziata), avviso(3, diSera)));

            List<AdminDTO.AvvisoResponse> avvisi = service.getAvvisi();

            assertThat(avvisi).extracting(AdminDTO.AvvisoResponse::getIdPartita).containsExactly(22, 21);
            AdminDTO.AvvisoResponse primo = avvisi.get(0);
            assertThat(primo.getId()).isEqualTo(3);
            assertThat(primo.getNomeCampo()).isEqualTo("Notturno");
            assertThat(primo.getDataPartita()).isEqualTo(ADESSO.toLocalDate());
            assertThat(primo.getOraInizio()).isEqualTo(LocalTime.of(20, 0));
            assertThat(primo.getOraFine()).isEqualTo(LocalTime.of(21, 30));
            assertThat(primo.getPostiLiberi()).isEqualTo(3);
        }

        @Test
        void partitaDiIeriDopoMezzanotteNonAncoraIniziataRestaInBacheca() {
            // ADESSO è alle 12: un campo aperto dalle 13 alle 13 considera la partita delle 12:30 "dopo mezzanotte"
            Campo tuttoIlGiorno = new Campo(3, "H24", false, LocalTime.of(13, 0), LocalTime.of(13, 0));
            Partita p = partitaIn(tuttoIlGiorno, ADESSO.toLocalDate().minusDays(1), LocalTime.of(12, 30));
            p.setId(21);
            when(avvisoRepository.findAperti(StatoPartita.ANNULLATA, ADESSO.toLocalDate().minusDays(1)))
                    .thenReturn(List.of(avviso(1, p)));

            assertThat(service.getAvvisi()).hasSize(1);
        }

        private Avviso avviso(int id, Partita partita) {
            Avviso avviso = new Avviso();
            avviso.setId(id);
            avviso.setPartita(partita);
            return avviso;
        }
    }

    // ============================================
    // Gestione livelli
    // ============================================
    @Nested
    class GestioneLivelli {

        @Test
        void cercaUsaLoStessoPatternDegliInvitabili() {
            Utente mario = utente(TEL_A, 2.5, 7);
            when(utenteRepository.findNonAdmin("%ros!_%")).thenReturn(List.of(mario));

            List<AdminDTO.UtenteResponse> risposta = service.getUtenti("  ROS_ ");

            assertThat(risposta).hasSize(1);
            AdminDTO.UtenteResponse riga = risposta.get(0);
            assertThat(riga.getTelefono()).isEqualTo(TEL_A);
            assertThat(riga.getNome()).isEqualTo("Nome" + TEL_A);
            assertThat(riga.getCognome()).isEqualTo("Cognome");
            assertThat(riga.getLivello()).isEqualTo(2.5);
            assertThat(riga.getPunteggio()).isEqualTo(7);
        }

        @Test
        void senzaCercaPrendeTutti() {
            when(utenteRepository.findNonAdmin("%%")).thenReturn(List.of());

            assertThat(service.getUtenti(null)).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(doubles = {0.0, 0.5, 2.5, 5.0})
        void livelliValidi(double livello) {
            Utente mario = utente(TEL_A, 1.0, 0);
            when(utenteRepository.findById(TEL_A)).thenReturn(Optional.of(mario));

            AdminDTO.UtenteResponse risposta = service.modificaLivello(TEL_A, livello);

            assertThat(mario.getLivello()).isEqualTo(livello);
            assertThat(risposta.getLivello()).isEqualTo(livello);
        }

        @ParameterizedTest
        @ValueSource(doubles = {-0.5, 5.5, 2.25, 0.1, Double.NaN, Double.POSITIVE_INFINITY})
        void livelliNonValidi(double livello) {
            assertThatThrownBy(() -> service.modificaLivello(TEL_A, livello))
                    .isInstanceOf(RichiestaNonValidaException.class)
                    .hasMessage("Il livello deve essere tra 0 e 5 con passo 0,5");
            verifyNoInteractions(utenteRepository);
        }

        @Test
        void livelloMancante() {
            assertThatThrownBy(() -> service.modificaLivello(TEL_A, null))
                    .isInstanceOf(RichiestaNonValidaException.class)
                    .hasMessage("Il livello deve essere tra 0 e 5 con passo 0,5");
        }

        @Test
        void utenteInesistente404() {
            when(utenteRepository.findById(TEL_A)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.modificaLivello(TEL_A, 2.0))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Utente non trovato");
        }

        @Test
        void telefonoDiUnAdmin404SenzaLock() {
            when(utenteRepository.findById(TEL_ADMIN)).thenReturn(Optional.of(admin));

            assertThatThrownBy(() -> service.modificaLivello(TEL_ADMIN, 2.0))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Utente non trovato");
            assertThat(admin.getLivello()).isEqualTo(1.0);
            verifyNoInteractions(entityManager);
        }

        @Test
        void nonSovrascriveUnPunteggioAggiornatoNelFrattempo() {
            // L'utente è stato letto con 5 punti, poi una registrazione in parallelo lo ha portato a 8:
            // la refresh con il lock deve riportare 8, che Hibernate riscriverà insieme al livello
            Utente mario = utente(TEL_A, 1.0, 5);
            punteggiSulDatabase.put(TEL_A, 8);
            when(utenteRepository.findById(TEL_A)).thenReturn(Optional.of(mario));

            AdminDTO.UtenteResponse risposta = service.modificaLivello(TEL_A, 3.0);

            verify(entityManager).refresh(mario, LockModeType.PESSIMISTIC_WRITE);
            assertThat(mario.getPunteggio()).isEqualTo(8);
            assertThat(risposta.getPunteggio()).isEqualTo(8);
            assertThat(risposta.getLivello()).isEqualTo(3.0);
        }
    }

    // ============================================
    // Modifica dei partecipanti
    // ============================================
    @Nested
    class Partecipanti {

        @Test
        void aggiungeSostituisceERimuoveToccandoSoloLeDifferenze() {
            Partita p = partita(StatoPartita.CREATA, 18, TEL_A, TEL_B, TEL_C);
            Partecipazione creatore = p.getPartecipazioni().get(0);
            creatore.setConfermata(true);
            Partecipazione resta = p.getPartecipazioni().get(1);
            resta.setInvitato(true);
            resta.setConfermata(true);
            Partecipazione esce = p.getPartecipazioni().get(2);
            int idCreatore = creatore.getId();
            stubLock(p);
            Utente entra = stubUtente(TEL_D);
            stubIdAlFlush(p);

            PartitaDTO.DettaglioResponse risposta = service.modificaPartecipanti(ID_PARTITA, List.of(TEL_D, TEL_B, TEL_A));

            assertThat(p.getPartecipazioni()).doesNotContain(esce);
            assertThat(p.getPartecipazioni()).hasSize(3);
            // Chi resta è la stessa riga, con i suoi dati
            assertThat(p.getPartecipazioni().get(0)).isSameAs(creatore);
            assertThat(creatore.getId()).isEqualTo(idCreatore);
            assertThat(resta.getInvitato()).isTrue();
            assertThat(resta.getConfermata()).isTrue();
            Partecipazione nuova = p.getPartecipazioni().get(2);
            assertThat(nuova.getUtente()).isSameAs(entra);
            assertThat(nuova.getTipo()).isEqualTo(TipoPartecipazione.UTENTE);
            assertThat(nuova.getInvitato()).isFalse();
            assertThat(nuova.getConfermata()).isFalse();
            assertThat(p.getStato()).isEqualTo(StatoPartita.CREATA);
            verify(partitaRepository).flush();
            // Il nuovo posto ha già l'id nella risposta
            assertThat(risposta.getPosti()).extracting(PartitaDTO.PostoResponse::getIdPosto).doesNotContainNull();
            assertThat(risposta.getPosti().get(0).getTelefono()).isEqualTo(TEL_A);
            assertAzioniTutteFalse(risposta);
        }

        @Test
        void iNuoviEntranoInOrdineDiTelefonoComeILockDeiPunteggi() {
            Partita p = partita(StatoPartita.CREATA, 18, TEL_A);
            stubLock(p);
            stubUtente(TEL_D);
            stubUtente(TEL_B);
            stubUtente(TEL_C);

            service.modificaPartecipanti(ID_PARTITA, List.of(TEL_A, TEL_D, TEL_B, TEL_C));

            assertThat(p.getPartecipazioni()).extracting(pa -> pa.getUtente().getTelefono())
                    .containsExactly(TEL_A, TEL_B, TEL_C, TEL_D);
        }

        @Test
        void stessoElencoNonCambiaNullaESenzaLeggereUtenti() {
            Partita p = partita(StatoPartita.CREATA, 18, TEL_A, TEL_B);
            List<Partecipazione> prima = new ArrayList<>(p.getPartecipazioni());
            stubLock(p);

            service.modificaPartecipanti(ID_PARTITA, List.of(TEL_B, TEL_A));

            assertThat(p.getPartecipazioni()).containsExactlyElementsOf(prima);
            verify(utenteRepository, never()).findById(any());
        }

        @Test
        void inUnaConfermataIlNuovoEConfermatoELaPartitaRestaConfermata() {
            Partita p = partita(StatoPartita.CONFERMATA, 18, TEL_A, TEL_B, TEL_C, TEL_D);
            p.getPartecipazioni().forEach(pa -> pa.setConfermata(true));
            stubLock(p);
            stubUtente(TEL_E);

            service.modificaPartecipanti(ID_PARTITA, List.of(TEL_A, TEL_B, TEL_C, TEL_E));

            Partecipazione nuova = p.getPartecipazioni().get(3);
            assertThat(nuova.getUtente().getTelefono()).isEqualTo(TEL_E);
            assertThat(nuova.getConfermata()).isTrue();
            assertThat(nuova.getInvitato()).isFalse();
            assertThat(p.getStato()).isEqualTo(StatoPartita.CONFERMATA);
        }

        @Test
        void unaConfermataConMenoGiocatoriRestaConfermata() {
            // La riporterà ad annullata lo scheduler all'inizio, se non viene completata (RF_GC_10)
            Partita p = partita(StatoPartita.CONFERMATA, 18, TEL_A, TEL_B, TEL_C, TEL_D);
            stubLock(p);

            service.modificaPartecipanti(ID_PARTITA, List.of(TEL_A, TEL_B));

            assertThat(p.getPartecipazioni()).hasSize(2);
            assertThat(p.getStato()).isEqualTo(StatoPartita.CONFERMATA);
        }

        @Test
        void confermaSeCompletaPassaAConfermata() {
            // Quattro posti tutti confermati, con un posto CAMPO che non conferma: la modifica li lascia e conferma
            Partita p = partita(StatoPartita.CREATA, 18, TEL_A, TEL_B, TEL_C);
            p.getPartecipazioni().forEach(pa -> pa.setConfermata(true));
            p.aggiungiPartecipazione(postoCampo());
            stubLock(p);

            PartitaDTO.DettaglioResponse risposta = service.modificaPartecipanti(ID_PARTITA, List.of(TEL_A, TEL_B, TEL_C));

            assertThat(p.getStato()).isEqualTo(StatoPartita.CONFERMATA);
            assertThat(risposta.getStato()).isEqualTo("confermata");
        }

        @Test
        void conUnNuovoNonConfermatoLaPartitaRestaCreata() {
            Partita p = partita(StatoPartita.CREATA, 18, TEL_A, TEL_B, TEL_C);
            p.getPartecipazioni().forEach(pa -> pa.setConfermata(true));
            stubLock(p);
            stubUtente(TEL_D);

            service.modificaPartecipanti(ID_PARTITA, List.of(TEL_A, TEL_B, TEL_C, TEL_D));

            assertThat(p.getPartecipazioni()).hasSize(4);
            assertThat(p.getStato()).isEqualTo(StatoPartita.CREATA);
        }

        @Test
        void iPostiCampoRestano() {
            Partita p = partita(StatoPartita.CREATA, 18, TEL_A, TEL_B);
            Partecipazione campoPosto = postoCampo();
            p.aggiungiPartecipazione(campoPosto);
            stubLock(p);

            service.modificaPartecipanti(ID_PARTITA, List.of(TEL_A));

            assertThat(p.getPartecipazioni()).hasSize(2).contains(campoPosto);
        }

        @Test
        void elencoVuotoOMancante400() {
            assertRichiestaNonValida(List.of(), "Almeno un giocatore deve essere un utente");
            assertRichiestaNonValida(null, "Almeno un giocatore deve essere un utente");
            verifyNoInteractions(partitaRepository);
        }

        @Test
        void telefoniDuplicati400() {
            assertRichiestaNonValida(List.of(TEL_A, TEL_B, TEL_A), "L'elenco dei partecipanti contiene telefoni duplicati");
            verifyNoInteractions(partitaRepository);
        }

        @Test
        void piuDi4Utenti400() {
            Partita p = partita(StatoPartita.CREATA, 18, TEL_A);
            stubLock(p);

            assertRichiestaNonValida(List.of(TEL_A, TEL_B, TEL_C, TEL_D, TEL_E),
                    "Una partita può avere al massimo 4 giocatori");
        }

        @Test
        void iPostiCampoContanoNelLimite() {
            Partita p = partita(StatoPartita.CREATA, 18, TEL_A);
            p.aggiungiPartecipazione(postoCampo());
            p.aggiungiPartecipazione(postoCampo());
            stubLock(p);

            assertRichiestaNonValida(List.of(TEL_A, TEL_B, TEL_C), "Una partita può avere al massimo 4 giocatori");
            assertThat(p.getPartecipazioni()).hasSize(3);
        }

        @Test
        void partitaInesistente404() {
            when(partitaRepository.findByIdPerAggiornamento(ID_PARTITA)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.modificaPartecipanti(ID_PARTITA, List.of(TEL_A)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Partita non trovata con id 7");
        }

        @Test
        void utenteInesistente404SenzaToccareLaPartita() {
            Partita p = partita(StatoPartita.CREATA, 18, TEL_A, TEL_B);
            stubLock(p);
            when(utenteRepository.findById(TEL_C)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.modificaPartecipanti(ID_PARTITA, List.of(TEL_A, TEL_C)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Utente non trovato");
            assertThat(p.getPartecipazioni()).hasSize(2);
        }

        @Test
        void telefonoNullNonCorrispondeANessuno404() {
            stubLock(partita(StatoPartita.CREATA, 18, TEL_A));

            assertThatThrownBy(() -> service.modificaPartecipanti(ID_PARTITA, Arrays.asList(TEL_A, null)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Utente non trovato");
        }

        @ParameterizedTest
        @EnumSource(value = StatoPartita.class, names = {"ANNULLATA", "GIOCATA"})
        void partitaAnnullataOGiocata409(StatoPartita stato) {
            Partita p = partita(stato, 18, TEL_A, TEL_B);
            stubLock(p);

            assertThatThrownBy(() -> service.modificaPartecipanti(ID_PARTITA, List.of(TEL_A)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Si possono modificare solo le partite non annullate e non giocate");
            assertThat(p.getPartecipazioni()).hasSize(2);
        }

        @Test
        void creatoreNonRimovibile409() {
            Partita p = partita(StatoPartita.CREATA, 18, TEL_A, TEL_B);
            stubLock(p);

            assertThatThrownBy(() -> service.modificaPartecipanti(ID_PARTITA, List.of(TEL_B)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Il creatore non può essere rimosso: annulla la partita");
            assertThat(p.getPartecipazioni()).hasSize(2);
        }

        @Test
        void amministratoreNonPartecipa409() {
            Partita p = partita(StatoPartita.CREATA, 18, TEL_A);
            stubLock(p);
            when(utenteRepository.findById(TEL_ADMIN)).thenReturn(Optional.of(admin));

            assertThatThrownBy(() -> service.modificaPartecipanti(ID_PARTITA, List.of(TEL_A, TEL_ADMIN)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Un amministratore non può partecipare");
            assertThat(p.getPartecipazioni()).hasSize(1);
        }

        @Test
        void nonCePiuLimiteDiTempo() {
            Partita p = partitaIn(campo, ADESSO.toLocalDate(), LocalTime.of(12, 30));
            aggiungiUtenti(p, TEL_A, TEL_B);
            p.setCreatore(utenti.get(TEL_A));
            stubLock(p);

            service.modificaPartecipanti(ID_PARTITA, List.of(TEL_A));

            assertThat(p.getPartecipazioni()).hasSize(1);
        }

        private void assertRichiestaNonValida(List<String> telefoni, String messaggio) {
            assertThatThrownBy(() -> service.modificaPartecipanti(ID_PARTITA, telefoni))
                    .isInstanceOf(RichiestaNonValidaException.class)
                    .hasMessage(messaggio);
        }

        private Utente stubUtente(String telefono) {
            Utente nuovo = utente(telefono, 2.0, 0);
            when(utenteRepository.findById(telefono)).thenReturn(Optional.of(nuovo));
            return nuovo;
        }

        // Come il cascade: al flush le righe nuove prendono l'id
        private void stubIdAlFlush(Partita p) {
            doAnswer(invocazione -> {
                p.getPartecipazioni().stream()
                        .filter(pa -> pa.getId() == null)
                        .forEach(pa -> pa.setId(prossimoIdPosto++));
                return null;
            }).when(partitaRepository).flush();
        }
    }

    // ============================================
    // Modifica del risultato
    // ============================================
    @Nested
    class ModificaRisultato {

        @Test
        void correzioneConCambioDiVincitoreStornaERicalcolaColMinimo0() {
            // a e b avevano vinto (+3), c e d perso (-1; d è passato da 1 a 0). Dopo lo storno: a 5, b 0, c 5, d 1.
            // Ora vincono c e d con medie uguali: +3. Perdono a e b: a -1, mentre b è a 0 e ha variazione 0.
            Partita p = giocata(8, 3, 4, 0);
            stubLock(p);

            PartitaDTO.DettaglioResponse risposta = service.modificaRisultato(ID_PARTITA,
                    request(p, "4-6", "3-6"), TEL_ADMIN);

            assertPunti(p, -1, 0, 3, 3);
            assertPunteggi(4, 0, 8, 4);
            assertThat(p.getPartecipazioni().get(0).getSquadra()).isEqualTo(1);
            assertThat(p.getPartecipazioni().get(3).getSquadra()).isEqualTo(2);
            assertThat(p.getStato()).isEqualTo(StatoPartita.GIOCATA);
            assertThat(risposta.getStato()).isEqualTo("giocata");
            assertThat(risposta.getRisultato().getSet()).hasSize(2);
            assertThat(risposta.getRisultato().getSquadraUtente()).isNull();
            assertThat(risposta.getRisultato().getEsito()).isNull();
            assertThat(risposta.getRisultato().getPuntiAssegnati()).isNull();
            assertAzioniTutteFalse(risposta);
        }

        @Test
        void correzioneConStessoVincitoreRiportaGliStessiPunti() {
            Partita p = giocata(8, 3, 4, 0);
            stubLock(p);

            service.modificaRisultato(ID_PARTITA, request(p, "6-1", "6-1"), TEL_ADMIN);

            assertPunti(p, 3, 3, -1, -1);
            assertPunteggi(8, 3, 4, 0);
        }

        @Test
        void correzioneConSquadreCambiate() {
            // Stessi set, ma ora a e c vincono contro b e d
            Partita p = giocata(8, 3, 4, 0);
            stubLock(p);
            List<Partecipazione> posti = p.getPartecipazioni();
            PartitaDTO.RisultatoRequest request = new PartitaDTO.RisultatoRequest(
                    List.of(posti.get(0).getId(), posti.get(2).getId()),
                    List.of(posti.get(1).getId(), posti.get(3).getId()),
                    set("6-4", "6-3"));

            service.modificaRisultato(ID_PARTITA, request, TEL_ADMIN);

            assertThat(posti).extracting(Partecipazione::getSquadra).containsExactly(1, 2, 1, 2);
            assertPunti(p, 3, 0, 3, -1);
            // Dopo lo storno: 5, 0, 5, 1
            assertPunteggi(8, 0, 8, 0);
        }

        @Test
        void stornoColMinimo0SeNelFrattempoIlGiocatoreHaPersoPunti() {
            // a aveva ricevuto +3 ma oggi ha 1 punto: lo storno si ferma a 0, poi riceve i nuovi punti
            Partita p = giocata(1, 3, 4, 1);
            stubLock(p);

            service.modificaRisultato(ID_PARTITA, request(p, "6-1", "6-1"), TEL_ADMIN);

            assertPunti(p, 3, 3, -1, -1);
            assertPunteggi(3, 3, 4, 1);
        }

        @Test
        void usaILivelliAttuali() {
            // Dopo la partita a è salito di livello: la media dei vincitori supera quella dei perdenti, quindi +1
            Partita p = giocata(8, 3, 4, 0);
            utenti.get(TEL_A).setLivello(5.0);
            stubLock(p);

            service.modificaRisultato(ID_PARTITA, request(p, "6-4", "6-3"), TEL_ADMIN);

            assertPunti(p, 1, 1, -1, -1);
            assertPunteggi(6, 1, 4, 0);
        }

        @Test
        void daTreSetADueAzzeraIlTerzo() {
            Partita p = giocata(8, 3, 4, 0);
            p.setSet2Sq1(3);
            p.setSet2Sq2(6);
            p.setSet3Sq1(6);
            p.setSet3Sq2(2);
            stubLock(p);

            PartitaDTO.DettaglioResponse risposta = service.modificaRisultato(ID_PARTITA,
                    request(p, "6-0", "6-0"), TEL_ADMIN);

            assertThat(p.getSet1Sq1()).isEqualTo(6);
            assertThat(p.getSet2Sq2()).isEqualTo(0);
            assertThat(p.getSet3Sq1()).isNull();
            assertThat(p.getSet3Sq2()).isNull();
            assertThat(risposta.getRisultato().getSet()).hasSize(2);
        }

        @Test
        void daDueSetATreSalvaIlTerzo() {
            Partita p = giocata(8, 3, 4, 0);
            stubLock(p);

            service.modificaRisultato(ID_PARTITA, request(p, "6-4", "3-6", "2-6"), TEL_ADMIN);

            assertThat(p.getSet3Sq1()).isEqualTo(2);
            assertThat(p.getSet3Sq2()).isEqualTo(6);
            assertPunti(p, -1, 0, 3, 3);
        }

        @Test
        void registranteEDataSonoQuelliDellaCorrezione() {
            Partita p = giocata(8, 3, 4, 0);
            p.setRegistrante(utenti.get(TEL_C));
            p.setDataRisultato(ADESSO.minusDays(3));
            stubLock(p);

            service.modificaRisultato(ID_PARTITA, request(p, "6-4", "6-3"), TEL_ADMIN);

            assertThat(p.getRegistrante()).isSameAs(admin);
            assertThat(p.getDataRisultato()).isEqualTo(ADESSO);
        }

        @Test
        void confermataRegistraSenzaLimiteDiTempoESenzaStorno() {
            Partita p = partitaCon4(StatoPartita.CONFERMATA, 5, 5, 5, 5);
            // Ben oltre fine + 24 h
            p.setDataPartita(ADESSO.toLocalDate().minusDays(10));
            stubLock(p);

            service.modificaRisultato(ID_PARTITA, request(p, "6-4", "6-3"), TEL_ADMIN);

            assertThat(p.getStato()).isEqualTo(StatoPartita.GIOCATA);
            assertThat(p.getRegistrante()).isSameAs(admin);
            assertPunti(p, 3, 3, -1, -1);
            assertPunteggi(8, 8, 4, 4);
        }

        @Test
        void iPostiCampoNonRicevonoPuntiEContanoNellaMedia() {
            Partita p = partita(StatoPartita.CONFERMATA, 18, TEL_A, TEL_B);
            p.aggiungiPartecipazione(postoCampo());
            p.aggiungiPartecipazione(postoCampo());
            salvaPunteggi();
            stubLock(p);

            service.modificaRisultato(ID_PARTITA, request(p, "6-4", "6-3"), TEL_ADMIN);

            // Utenti di livello 2 contro giocatori campo di livello 1: la media dei perdenti è minore
            assertThat(p.getPartecipazioni()).extracting(Partecipazione::getPuntiAssegnati)
                    .containsExactly(1, 1, null, null);
        }

        @ParameterizedTest
        @EnumSource(value = StatoPartita.class, names = {"CREATA", "ANNULLATA"})
        void altriStati409(StatoPartita stato) {
            Partita p = partitaCon4(stato, 5, 5, 5, 5);
            stubLock(p);

            assertThatThrownBy(() -> service.modificaRisultato(ID_PARTITA, request(p, "6-4", "6-3"), TEL_ADMIN))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Il risultato si può modificare solo per le partite confermate o giocate");
            assertThat(p.getStato()).isEqualTo(stato);
            assertThat(p.getSet1Sq1()).isNull();
            verifyNoInteractions(entityManager);
        }

        @Test
        void partitaInesistente404() {
            when(partitaRepository.findByIdPerAggiornamento(ID_PARTITA)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.modificaRisultato(ID_PARTITA, new PartitaDTO.RisultatoRequest(), TEL_ADMIN))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Partita non trovata con id 7");
        }

        @Test
        void stesseValidazioniDelRisultatoSenzaToccareIPunti() {
            Partita p = giocata(8, 3, 4, 0);
            stubLock(p);
            List<Integer> s1 = List.of(p.getPartecipazioni().get(0).getId(), p.getPartecipazioni().get(1).getId());
            List<Integer> s2 = List.of(p.getPartecipazioni().get(2).getId(), p.getPartecipazioni().get(3).getId());

            assertNonValido(new PartitaDTO.RisultatoRequest(List.of(s1.get(0)), s2, set("6-4", "6-3")),
                    "Ogni squadra deve avere 2 giocatori");
            assertNonValido(new PartitaDTO.RisultatoRequest(s1, List.of(s2.get(0), 999), set("6-4", "6-3")),
                    "Le squadre devono contenere i 4 posti della partita");
            assertNonValido(new PartitaDTO.RisultatoRequest(s1, s1, set("6-4", "6-3")),
                    "Le squadre devono contenere i 4 posti della partita");
            assertNonValido(new PartitaDTO.RisultatoRequest(s1, s2, set("6-6", "6-3")),
                    "Il set 1 non è valido: i game vanno da 0 a 6 e il set non può finire in parità");
            assertNonValido(new PartitaDTO.RisultatoRequest(s1, s2, set("6-4", "3-6")),
                    "Il risultato deve avere 2 o 3 set e una squadra deve vincerne esattamente 2");
            assertNonValido(new PartitaDTO.RisultatoRequest(s1, s2, set("6-4", "6-3", "6-1")),
                    "Il terzo set non va inserito se una squadra ha vinto i primi due");

            assertPunti(p, 3, 3, -1, -1);
            assertPunteggi(8, 3, 4, 0);
            verifyNoInteractions(entityManager);
        }

        @Test
        void loStornoEScrittoPrimaDiRileggereGliUtentiPerINuoviPunti() {
            Partita p = giocata(8, 3, 4, 0);
            stubLock(p);

            service.modificaRisultato(ID_PARTITA, request(p, "6-4", "6-3"), TEL_ADMIN);

            InOrder ordine = inOrder(entityManager);
            ordine.verify(entityManager).refresh(utenti.get(TEL_A), LockModeType.PESSIMISTIC_WRITE);
            ordine.verify(entityManager).flush();
            ordine.verify(entityManager).refresh(utenti.get(TEL_A), LockModeType.PESSIMISTIC_WRITE);
            // Il flush arriva prima di scrivere il registrante sulla partita: vedi PunteggiService.bloccaUtenti
            verify(entityManager, times(1)).flush();
        }

        private void assertNonValido(PartitaDTO.RisultatoRequest request, String messaggio) {
            assertThatThrownBy(() -> service.modificaRisultato(ID_PARTITA, request, TEL_ADMIN))
                    .isInstanceOf(RichiestaNonValidaException.class)
                    .hasMessage(messaggio);
        }
    }

    // ============================================
    // Modifica dello stato
    // ============================================
    @Nested
    class ModificaStato {

        @Test
        void daGiocataAdAnnullataStornaSenzaRicalcolo() {
            Partita p = giocata(8, 3, 4, 0);
            stubLock(p);

            PartitaDTO.DettaglioResponse risposta = service.modificaStato(ID_PARTITA, "annullata");

            assertThat(p.getStato()).isEqualTo(StatoPartita.ANNULLATA);
            assertThat(risposta.getStato()).isEqualTo("annullata");
            assertPunti(p, null, null, null, null);
            assertPunteggi(5, 0, 5, 1);
            assertAzioniTutteFalse(risposta);
        }

        @Test
        void daAnnullataAGiocataSenzaRisultatoNonAssegnaPunti() {
            Partita p = partitaCon4(StatoPartita.ANNULLATA, 5, 5, 5, 5);
            stubLock(p);

            service.modificaStato(ID_PARTITA, "giocata");

            assertThat(p.getStato()).isEqualTo(StatoPartita.GIOCATA);
            assertPunti(p, null, null, null, null);
            assertPunteggi(5, 5, 5, 5);
            verifyNoInteractions(entityManager);
        }

        @ParameterizedTest
        @ValueSource(strings = {"creata", "confermata", "annullata"})
        void aOgniUscitaDaGiocataStorna(String stato) {
            Partita p = giocata(8, 3, 4, 0);
            stubLock(p);

            PartitaDTO.DettaglioResponse risposta = service.modificaStato(ID_PARTITA, stato);

            assertThat(risposta.getStato()).isEqualTo(stato);
            assertPunti(p, null, null, null, null);
            assertPunteggi(5, 0, 5, 1);
            // Il risultato resta salvato: serve se la partita torna a GIOCATA
            assertThat(RegoleRisultatoPartita.risultatoRegistrato(p)).isTrue();
        }

        @Test
        void daGiocataAGiocataNessunDoppioConteggio() {
            Partita p = giocata(8, 3, 4, 0);
            stubLock(p);

            PartitaDTO.DettaglioResponse risposta = service.modificaStato(ID_PARTITA, "giocata");

            assertThat(risposta.getStato()).isEqualTo("giocata");
            assertPunti(p, 3, 3, -1, -1);
            assertPunteggi(8, 3, 4, 0);
            verifyNoInteractions(entityManager);
        }

        @ParameterizedTest
        @EnumSource(value = StatoPartita.class, names = {"CREATA", "CONFERMATA", "ANNULLATA"})
        void ingressoInGiocataConRisultatoAssegnaIPunti(StatoPartita stato) {
            Partita p = conRisultatoSenzaPunti(stato, 5, 0, 5, 1);
            stubLock(p);

            PartitaDTO.DettaglioResponse risposta = service.modificaStato(ID_PARTITA, "giocata");

            assertThat(risposta.getStato()).isEqualTo("giocata");
            assertPunti(p, 3, 3, -1, -1);
            assertPunteggi(8, 3, 4, 0);
        }

        @Test
        void ingressoInGiocataUsaILivelliAttuali() {
            Partita p = conRisultatoSenzaPunti(StatoPartita.ANNULLATA, 5, 5, 5, 5);
            stubLock(p);
            // Ora i vincitori hanno un livello medio più alto dei perdenti: la vittoria vale +1 e non +3
            utenti.get(TEL_A).setLivello(4.0);
            utenti.get(TEL_B).setLivello(4.0);

            service.modificaStato(ID_PARTITA, "giocata");

            assertPunti(p, 1, 1, -1, -1);
            assertPunteggi(6, 6, 4, 4);
        }

        @Test
        void slotOccupatoEntrandoInGiocataNonAssegnaPunti() {
            Partita p = conRisultatoSenzaPunti(StatoPartita.ANNULLATA, 5, 5, 5, 5);
            stubLock(p);
            doThrow(new DataIntegrityViolationException("x", new RuntimeException(
                    "Duplicate entry '1-2026-11-05-18:00:00-1' for key 'partita.uk_partita_slot'")))
                    .when(partitaRepository).flush();

            assertThatThrownBy(() -> service.modificaStato(ID_PARTITA, "giocata"))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Lo slot è già occupato da un'altra partita");
            assertPunti(p, null, null, null, null);
            verifyNoInteractions(entityManager);
        }

        @Test
        void daConfermataAdAnnullataNonToccaIPunti() {
            Partita p = partitaCon4(StatoPartita.CONFERMATA, 5, 5, 5, 5);
            stubLock(p);

            service.modificaStato(ID_PARTITA, "annullata");

            assertThat(p.getStato()).isEqualTo(StatoPartita.ANNULLATA);
            verifyNoInteractions(entityManager);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "boh", "ANNULLATA", "Creata", " creata"})
        void statoNonValido400SenzaLeggereLaPartita(String stato) {
            assertThatThrownBy(() -> service.modificaStato(ID_PARTITA, stato))
                    .isInstanceOf(RichiestaNonValidaException.class)
                    .hasMessage("Stato non valido");
            verifyNoInteractions(partitaRepository);
        }

        @Test
        void statoMancante400() {
            assertThatThrownBy(() -> service.modificaStato(ID_PARTITA, null))
                    .isInstanceOf(RichiestaNonValidaException.class)
                    .hasMessage("Stato non valido");
        }

        @Test
        void partitaInesistente404() {
            when(partitaRepository.findByIdPerAggiornamento(ID_PARTITA)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.modificaStato(ID_PARTITA, "creata"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Partita non trovata con id 7");
        }

        @Test
        void slotOccupatoAlFlush409() {
            Partita p = partitaCon4(StatoPartita.ANNULLATA, 5, 5, 5, 5);
            stubLock(p);
            doThrow(new DataIntegrityViolationException("x", new RuntimeException(
                    "Duplicate entry '1-2026-11-05-18:00:00-1' for key 'partita.uk_partita_slot'")))
                    .when(partitaRepository).flush();

            assertThatThrownBy(() -> service.modificaStato(ID_PARTITA, "creata"))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Lo slot è già occupato da un'altra partita");
        }

        @Test
        void altreViolazioniNonDiventanoSlotOccupato() {
            Partita p = partitaCon4(StatoPartita.ANNULLATA, 5, 5, 5, 5);
            stubLock(p);
            DataIntegrityViolationException altra = new DataIntegrityViolationException("altro vincolo");
            doThrow(altra).when(partitaRepository).flush();

            assertThatThrownBy(() -> service.modificaStato(ID_PARTITA, "creata")).isSameAs(altra);
        }
    }

    // ============================================
    // Annullamento
    // ============================================
    @Nested
    class Annullamento {

        @Test
        void giocataStornaIPunti() {
            Partita p = giocata(8, 3, 4, 0);
            stubLock(p);

            PartitaDTO.DettaglioResponse risposta = service.annulla(ID_PARTITA);

            assertThat(p.getStato()).isEqualTo(StatoPartita.ANNULLATA);
            assertThat(risposta.getStato()).isEqualTo("annullata");
            assertPunti(p, null, null, null, null);
            // Lo storno restituisce anche il punto tolto al perdente
            assertPunteggi(5, 0, 5, 1);
            assertAzioniTutteFalse(risposta);
        }

        @Test
        void giocataStornoColMinimo0() {
            Partita p = giocata(1, 3, 4, 0);
            stubLock(p);

            service.annulla(ID_PARTITA);

            assertPunteggi(0, 0, 5, 1);
        }

        @Test
        void risultatoResta() {
            Partita p = giocata(8, 3, 4, 0);
            stubLock(p);

            PartitaDTO.DettaglioResponse risposta = service.annulla(ID_PARTITA);

            assertThat(p.getSet1Sq1()).isEqualTo(6);
            assertThat(risposta.getRisultato()).isNotNull();
        }

        @ParameterizedTest
        @EnumSource(value = StatoPartita.class, names = {"CREATA", "CONFERMATA"})
        void nonGiocataSoloCambioDiStatoInQualsiasiMomento(StatoPartita stato) {
            Partita p = partitaIn(campo, ADESSO.toLocalDate(), LocalTime.of(12, 30));
            aggiungiUtenti(p, TEL_A);
            p.setCreatore(utenti.get(TEL_A));
            p.setStato(stato);
            stubLock(p);

            service.annulla(ID_PARTITA);

            assertThat(p.getStato()).isEqualTo(StatoPartita.ANNULLATA);
            verifyNoInteractions(entityManager);
        }

        @Test
        void giaAnnullata409() {
            Partita p = partitaCon4(StatoPartita.ANNULLATA, 5, 5, 5, 5);
            stubLock(p);

            assertThatThrownBy(() -> service.annulla(ID_PARTITA))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("La partita è già annullata");
        }

        @Test
        void partitaInesistente404() {
            when(partitaRepository.findByIdPerAggiornamento(ID_PARTITA)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.annulla(ID_PARTITA))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Partita non trovata con id 7");
        }
    }

    // ============================================
    // Percorsi tra gli stati: i punti esistono solo sulle partite GIOCATA
    // ============================================
    @Nested
    class PercorsiDeiPunti {

        @Test
        void giocataConfermataAnnullataNessunPuntoResiduo() {
            Partita p = giocata(8, 3, 4, 0);
            stubLock(p);

            service.modificaStato(ID_PARTITA, "confermata");
            service.modificaStato(ID_PARTITA, "annullata");

            assertThat(p.getStato()).isEqualTo(StatoPartita.ANNULLATA);
            assertPunti(p, null, null, null, null);
            assertPunteggi(5, 0, 5, 1);
        }

        @Test
        void giocataConfermataAnnullaNessunPuntoResiduo() {
            Partita p = giocata(8, 3, 4, 0);
            stubLock(p);

            service.modificaStato(ID_PARTITA, "confermata");
            service.annulla(ID_PARTITA);

            assertPunti(p, null, null, null, null);
            assertPunteggi(5, 0, 5, 1);
        }

        @Test
        void giocataCreataSuPartitaIniziataPoiAnnullataDalloSchedulerNessunPuntoResiduo() {
            Partita p = giocata(8, 3, 4, 0);
            assertThat(RegoleTempoPartita.iniziata(p, ADESSO)).isTrue();
            stubLock(p);
            service.modificaStato(ID_PARTITA, "creata");

            PlatformTransactionManager transazioni = org.mockito.Mockito.mock(PlatformTransactionManager.class);
            when(transazioni.getTransaction(any())).thenAnswer(invocazione -> new SimpleTransactionStatus());
            when(partitaRepository.findCandidateScadenze(any(), any(), any(Integer.class), any()))
                    .thenReturn(List.of(p));
            new ScadenzePartiteService(partitaRepository, avvisoRepository, transazioni,
                    Clock.fixed(ADESSO.atZone(ZONA).toInstant(), ZONA)).eseguiGiro();

            assertThat(p.getStato()).isEqualTo(StatoPartita.ANNULLATA);
            assertPunti(p, null, null, null, null);
            assertPunteggi(5, 0, 5, 1);
        }

        @Test
        void giocataCreataPoiRimozioneDiUnGiocatoreNessunPuntoPerso() {
            Partita p = giocata(8, 3, 4, 0);
            stubLock(p);

            service.modificaStato(ID_PARTITA, "creata");
            service.modificaPartecipanti(ID_PARTITA, List.of(TEL_A, TEL_B, TEL_C));

            assertThat(p.getPartecipazioni()).hasSize(3);
            assertThat(p.getPartecipazioni()).extracting(Partecipazione::getPuntiAssegnati).containsOnlyNulls();
            // d aveva perso 1 punto: gli è stato restituito prima che il suo posto sparisse
            assertPunteggi(5, 0, 5, 1);
        }

        @Test
        void partecipantiRidottiLaPartitaTornaGiocataSenzaPunti() {
            Partita p = giocata(8, 3, 4, 0);
            stubLock(p);
            service.modificaStato(ID_PARTITA, "creata");
            service.modificaPartecipanti(ID_PARTITA, List.of(TEL_A, TEL_B, TEL_C));

            PartitaDTO.DettaglioResponse risposta = service.modificaStato(ID_PARTITA, "giocata");

            assertThat(risposta.getStato()).isEqualTo("giocata");
            assertThat(p.getPartecipazioni()).extracting(Partecipazione::getPuntiAssegnati).containsOnlyNulls();
            assertPunteggi(5, 0, 5, 1);
        }

        @Test
        void chiEntraDopoIlRisultatoNonPerdePuntiSenzaAverGiocato() {
            Partita p = giocata(8, 3, 4, 0);
            utente(TEL_E, 2.0, 7);
            stubLock(p);
            when(utenteRepository.findById(TEL_E)).thenReturn(Optional.of(utenti.get(TEL_E)));
            service.modificaStato(ID_PARTITA, "creata");
            service.modificaPartecipanti(ID_PARTITA, List.of(TEL_A, TEL_B, TEL_C, TEL_E));

            service.modificaStato(ID_PARTITA, "giocata");

            assertThat(p.getPartecipazioni()).extracting(Partecipazione::getPuntiAssegnati).containsOnlyNulls();
            assertThat(utenti.get(TEL_E).getPunteggio()).isEqualTo(7);
            assertPunteggi(5, 0, 5, 1);
        }

        @Test
        void avantiEIndietroLoStessoPunteggio() {
            Partita p = giocata(8, 3, 4, 0);
            stubLock(p);

            service.modificaStato(ID_PARTITA, "confermata");
            service.modificaStato(ID_PARTITA, "giocata");
            // Il commit della richiesta: qui non c'è una transazione vera che scrive i punti appena assegnati
            salvaPunteggi();
            service.modificaStato(ID_PARTITA, "annullata");
            assertPunteggi(5, 0, 5, 1);
            service.modificaStato(ID_PARTITA, "giocata");

            assertPunti(p, 3, 3, -1, -1);
            assertPunteggi(8, 3, 4, 0);
        }
    }

    // ============================================
    // Transazioni
    // ============================================
    @Test
    void iMetodiPubbliciApronoUnaTransazioneScrivibileSeNonSonoLetture() {
        // I metodi di PunteggiService sono MANDATORY: senza una transazione qui fallirebbero
        for (Method metodo : AdminService.class.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(metodo.getModifiers())) {
                Transactional transazione = metodo.getAnnotation(Transactional.class);
                assertThat(transazione).as(metodo.getName()).isNotNull();
                assertThat(transazione.readOnly()).as(metodo.getName()).isEqualTo(metodo.getName().startsWith("get"));
            }
        }
    }

    // ============================================
    // Helper
    // ============================================

    private void stubLock(Partita p) {
        when(partitaRepository.findByIdPerAggiornamento(ID_PARTITA)).thenReturn(Optional.of(p));
    }

    private Utente utente(String telefono, double livello, int punteggio) {
        Utente utente = new Utente(telefono, "Nome" + telefono, "Cognome", "x", LocalDate.of(1990, 1, 1), false, livello);
        utente.setPunteggio(punteggio);
        utenti.put(telefono, utente);
        punteggiSulDatabase.put(telefono, punteggio);
        return utente;
    }

    private void salvaPunteggi() {
        utenti.forEach((telefono, utente) -> punteggiSulDatabase.put(telefono, utente.getPunteggio()));
    }

    private Partita partitaIn(Campo campoPartita, LocalDate data, LocalTime inizio) {
        Partita p = new Partita();
        p.setId(ID_PARTITA);
        p.setCampo(campoPartita);
        p.setDataPartita(data);
        p.setOraInizio(inizio);
        p.setOraFine(inizio.plusMinutes(90));
        p.setTipo(TipoPartita.PUBBLICA);
        p.setStato(StatoPartita.CREATA);
        p.setCreatore(admin);
        return p;
    }

    // Il primo telefono è il creatore. Tutti di livello 2 con 0 punti, salvo modifiche.
    private Partita partita(StatoPartita stato, int ora, String... telefoni) {
        Partita p = partitaIn(campo, DATA, LocalTime.of(ora, 0));
        p.setStato(stato);
        aggiungiUtenti(p, telefoni);
        p.setCreatore(utenti.get(telefoni[0]));
        return p;
    }

    private void aggiungiUtenti(Partita p, String... telefoni) {
        for (String telefono : telefoni) {
            Utente u = utenti.containsKey(telefono) ? utenti.get(telefono) : utente(telefono, 2.0, 0);
            Partecipazione posto = new Partecipazione(TipoPartecipazione.UTENTE, u, false, false);
            posto.setId(prossimoIdPosto++);
            p.aggiungiPartecipazione(posto);
        }
    }

    private Partecipazione postoCampo() {
        Partecipazione posto = new Partecipazione(TipoPartecipazione.CAMPO, null, false, false);
        posto.setId(prossimoIdPosto++);
        return posto;
    }

    // Quattro utenti (a, b, c, d) con i punteggi indicati
    private Partita partitaCon4(StatoPartita stato, int a, int b, int c, int d) {
        Partita p = partita(stato, 18, TEL_A, TEL_B, TEL_C, TEL_D);
        impostaPunteggi(a, b, c, d);
        return p;
    }

    // Partita già registrata: a e b hanno vinto 6-4 6-3 con +3, c e d perso con -1. I punteggi sono quelli attuali.
    private Partita giocata(int a, int b, int c, int d) {
        Partita p = partitaCon4(StatoPartita.GIOCATA, a, b, c, d);
        p.setDataPartita(ADESSO.toLocalDate().minusDays(2));
        p.setSet1Sq1(6);
        p.setSet1Sq2(4);
        p.setSet2Sq1(6);
        p.setSet2Sq2(3);
        int[] punti = {3, 3, -1, -1};
        for (int i = 0; i < 4; i++) {
            Partecipazione posto = p.getPartecipazioni().get(i);
            posto.setPuntiAssegnati(punti[i]);
            posto.setSquadra(i < 2 ? 1 : 2);
            posto.setConfermata(true);
        }
        return p;
    }

    // Partita con il risultato salvato ma senza punti, come dopo un'uscita da GIOCATA
    private Partita conRisultatoSenzaPunti(StatoPartita stato, int a, int b, int c, int d) {
        Partita p = giocata(a, b, c, d);
        p.setStato(stato);
        p.getPartecipazioni().forEach(posto -> posto.setPuntiAssegnati(null));
        return p;
    }

    private void impostaPunteggi(int a, int b, int c, int d) {
        utenti.get(TEL_A).setPunteggio(a);
        utenti.get(TEL_B).setPunteggio(b);
        utenti.get(TEL_C).setPunteggio(c);
        utenti.get(TEL_D).setPunteggio(d);
        salvaPunteggi();
    }

    // Prima squadra: i primi due posti; set nel formato "6-4"
    private PartitaDTO.RisultatoRequest request(Partita p, String... set) {
        List<Partecipazione> posti = p.getPartecipazioni();
        return new PartitaDTO.RisultatoRequest(
                List.of(posti.get(0).getId(), posti.get(1).getId()),
                List.of(posti.get(2).getId(), posti.get(3).getId()),
                set(set));
    }

    private static List<PartitaDTO.SetRequest> set(String... set) {
        return Arrays.stream(set)
                .map(s -> s.split("-"))
                .map(g -> new PartitaDTO.SetRequest(Integer.valueOf(g[0]), Integer.valueOf(g[1])))
                .toList();
    }

    private static void assertPunti(Partita p, Integer a, Integer b, Integer c, Integer d) {
        assertThat(p.getPartecipazioni()).extracting(Partecipazione::getPuntiAssegnati).containsExactly(a, b, c, d);
    }

    private void assertPunteggi(int a, int b, int c, int d) {
        assertThat(utenti.get(TEL_A).getPunteggio()).as("a").isEqualTo(a);
        assertThat(utenti.get(TEL_B).getPunteggio()).as("b").isEqualTo(b);
        assertThat(utenti.get(TEL_C).getPunteggio()).as("c").isEqualTo(c);
        assertThat(utenti.get(TEL_D).getPunteggio()).as("d").isEqualTo(d);
    }

    private static void assertAzioniTutteFalse(PartitaDTO.DettaglioResponse risposta) {
        PartitaDTO.AzioniResponse azioni = risposta.getAzioni();
        assertThat(azioni.getPuoInvitare()).isFalse();
        assertThat(azioni.getPuoEliminare()).isFalse();
        assertThat(azioni.getPuoUscire()).isFalse();
        assertThat(azioni.getPuoConfermare()).isFalse();
        assertThat(azioni.getPuoRegistrareRisultato()).isFalse();
    }
}
