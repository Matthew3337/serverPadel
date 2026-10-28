package com.padelbooking.api.service;

import com.padelbooking.api.dto.PrenotazioneDTO;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrenotazioneServiceTest {

    private static final int ID_CAMPO = 1;
    private static final String TEL_1 = "3330000001";
    private static final String TEL_2 = "3330000002";
    private static final String TEL_3 = "3330000003";
    private static final String TEL_4 = "3330000004";
    private static final String TEL_ESTRANEO = "3339999999";
    private static final String MESSAGGIO_SLOT_OCCUPATO = "Lo slot selezionato è già stato prenotato";

    @Mock
    private PartitaRepository partitaRepository;

    @Mock
    private UtenteRepository utenteRepository;

    @Mock
    private CampoService campoService;

    @InjectMocks
    private PrenotazioneService service;

    private Campo campoDiurno;
    private LocalDate domani;

    @BeforeEach
    void setUp() {
        campoDiurno = new Campo(ID_CAMPO, "Campo Centrale", true, LocalTime.of(8, 0), LocalTime.of(22, 0));
        domani = LocalDate.now().plusDays(1);
    }

    // ============================================
    // Creazione
    // ============================================
    @Nested
    class Creazione {

        @BeforeEach
        void stubSalvataggio() {
            // save restituisce la partita stessa con un id, come farebbe JPA
            org.mockito.Mockito.lenient().when(partitaRepository.save(any(Partita.class))).thenAnswer(inv -> {
                Partita p = inv.getArgument(0);
                p.setId(42);
                return p;
            });
        }

        @Test
        void creaPartitaPrivataDiDefaultConCreatoreEFineDopo90Minuti() {
            stubCampo(campoDiurno);
            stubNessunaPartitaAttiva(domani);
            stubUtenti(TEL_1, TEL_2, TEL_3, TEL_4);

            PrenotazioneDTO.Response r = service.create(richiesta(domani, LocalTime.of(10, 0), null,
                    TEL_1, TEL_2, TEL_3, TEL_4));

            Partita salvata = catturaPartitaSalvata();
            assertThat(salvata.getStato()).isEqualTo(StatoPartita.CREATA);
            assertThat(salvata.getTipo()).isEqualTo(TipoPartita.PRIVATA);
            assertThat(salvata.getCreatore().getTelefono()).isEqualTo(TEL_1);
            assertThat(salvata.getCampo()).isSameAs(campoDiurno);
            assertThat(salvata.getDataPartita()).isEqualTo(domani);
            assertThat(salvata.getOraInizio()).isEqualTo(LocalTime.of(10, 0));
            assertThat(salvata.getOraFine()).isEqualTo(LocalTime.of(11, 30));

            assertThat(r.getId()).isEqualTo(42);
            assertThat(r.getIdCampo()).isEqualTo(ID_CAMPO);
            assertThat(r.getNomeCampo()).isEqualTo("Campo Centrale");
            assertThat(r.getOraFine()).isEqualTo(LocalTime.of(11, 30));
            assertThat(r.getStato()).isEqualTo("creata");
        }

        @Test
        void creaPartitaPubblicaSeIndicato() {
            stubCampo(campoDiurno);
            stubNessunaPartitaAttiva(domani);
            stubUtenti(TEL_1);

            service.create(richiesta(domani, LocalTime.of(10, 0), "PUBBLICA", TEL_1, null, null, null));

            assertThat(catturaPartitaSalvata().getTipo()).isEqualTo(TipoPartita.PUBBLICA);
        }

        @Test
        void creaPartitaPrivataSeIndicato() {
            stubCampo(campoDiurno);
            stubNessunaPartitaAttiva(domani);
            stubUtenti(TEL_1);

            service.create(richiesta(domani, LocalTime.of(10, 0), "PRIVATA", TEL_1, null, null, null));

            assertThat(catturaPartitaSalvata().getTipo()).isEqualTo(TipoPartita.PRIVATA);
        }

        @Test
        void creaUnaPartecipazioneUtentePerOgniGiocatoreNonInvitataNonConfermata() {
            stubCampo(campoDiurno);
            stubNessunaPartitaAttiva(domani);
            stubUtenti(TEL_1, TEL_2, TEL_3, TEL_4);

            service.create(richiesta(domani, LocalTime.of(10, 0), null, TEL_1, TEL_2, TEL_3, TEL_4));

            Partita salvata = catturaPartitaSalvata();
            List<Partecipazione> part = salvata.getPartecipazioni();
            assertThat(part).hasSize(4);
            assertThat(part).extracting(pa -> pa.getUtente().getTelefono())
                    .containsExactly(TEL_1, TEL_2, TEL_3, TEL_4);
            assertThat(part).allSatisfy(pa -> {
                assertThat(pa.getTipo()).isEqualTo(TipoPartecipazione.UTENTE);
                assertThat(pa.getInvitato()).isFalse();
                assertThat(pa.getConfermata()).isFalse();
                assertThat(pa.getPartita()).isSameAs(salvata);
            });
        }

        @Test
        void conSoloGiocatore1CreaUnaSolaPartecipazioneEGiocatoriMancantiNull() {
            stubCampo(campoDiurno);
            stubNessunaPartitaAttiva(domani);
            stubUtenti(TEL_1);

            PrenotazioneDTO.Response r = service.create(richiesta(domani, LocalTime.of(10, 0), null,
                    TEL_1, null, null, null));

            assertThat(catturaPartitaSalvata().getPartecipazioni()).hasSize(1);
            assertThat(r.getTelefonoGiocatore1()).isEqualTo(TEL_1);
            assertThat(r.getTelefonoGiocatore2()).isNull();
            assertThat(r.getTelefonoGiocatore3()).isNull();
            assertThat(r.getTelefonoGiocatore4()).isNull();
        }

        @Test
        void giocatoriSaltatiNellaRichiestaVengonoCompattatiNellOrdine() {
            stubCampo(campoDiurno);
            stubNessunaPartitaAttiva(domani);
            stubUtenti(TEL_1, TEL_3);

            PrenotazioneDTO.Response r = service.create(richiesta(domani, LocalTime.of(10, 0), null,
                    TEL_1, null, TEL_3, null));

            assertThat(r.getTelefonoGiocatore1()).isEqualTo(TEL_1);
            assertThat(r.getTelefonoGiocatore2()).isEqualTo(TEL_3);
            assertThat(r.getTelefonoGiocatore3()).isNull();
            assertThat(r.getTelefonoGiocatore4()).isNull();
        }

        @Test
        void slotIdenticoGiaPrenotatoDa409() {
            stubCampo(campoDiurno);
            when(partitaRepository.findByCampoIdAndDataPartitaAndStatoNot(ID_CAMPO, domani, StatoPartita.ANNULLATA))
                    .thenReturn(List.of(partita(1, campoDiurno, utente(TEL_2), domani, LocalTime.of(10, 0), StatoPartita.CREATA)));

            assertThatThrownBy(() -> service.create(richiesta(domani, LocalTime.of(10, 0), null, TEL_1, null, null, null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage(MESSAGGIO_SLOT_OCCUPATO);
            verify(partitaRepository, never()).save(any());
        }

        @Test
        void sovrapposizioneParzialeDa409() {
            stubCampo(campoDiurno);
            // esistente 18:00-19:30, nuova 18:30-20:00
            when(partitaRepository.findByCampoIdAndDataPartitaAndStatoNot(ID_CAMPO, domani, StatoPartita.ANNULLATA))
                    .thenReturn(List.of(partita(1, campoDiurno, utente(TEL_2), domani, LocalTime.of(18, 0), StatoPartita.CONFERMATA)));

            assertThatThrownBy(() -> service.create(richiesta(domani, LocalTime.of(18, 30), null, TEL_1, null, null, null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage(MESSAGGIO_SLOT_OCCUPATO);
            verify(partitaRepository, never()).save(any());
        }

        @Test
        void slotAdiacenteNonESovrapposto() {
            stubCampo(campoDiurno);
            // esistente 18:00-19:30, nuova 19:30-21:00: si toccano ma non si sovrappongono
            when(partitaRepository.findByCampoIdAndDataPartitaAndStatoNot(ID_CAMPO, domani, StatoPartita.ANNULLATA))
                    .thenReturn(List.of(partita(1, campoDiurno, utente(TEL_2), domani, LocalTime.of(18, 0), StatoPartita.CREATA)));
            stubUtenti(TEL_1);

            PrenotazioneDTO.Response r = service.create(richiesta(domani, LocalTime.of(19, 30), null, TEL_1, null, null, null));

            assertThat(r.getOraInizio()).isEqualTo(LocalTime.of(19, 30));
        }

        @Test
        void interrogaSoloPartiteNonAnnullate() {
            stubCampo(campoDiurno);
            stubNessunaPartitaAttiva(domani);
            stubUtenti(TEL_1);

            service.create(richiesta(domani, LocalTime.of(10, 0), null, TEL_1, null, null, null));

            verify(partitaRepository).findByCampoIdAndDataPartitaAndStatoNot(ID_CAMPO, domani, StatoPartita.ANNULLATA);
        }

        @Test
        void violazioneVincoloSlotUnicoSulDbDa409() {
            stubCampo(campoDiurno);
            stubNessunaPartitaAttiva(domani);
            stubUtenti(TEL_1);
            SQLIntegrityConstraintViolationException causa = new SQLIntegrityConstraintViolationException(
                    "Duplicate entry '1-2026-11-01-10:00:00-1' for key 'partita.uk_partita_slot'");
            doThrow(new DataIntegrityViolationException("could not execute statement", causa))
                    .when(partitaRepository).save(any(Partita.class));

            assertThatThrownBy(() -> service.create(richiesta(domani, LocalTime.of(10, 0), null, TEL_1, null, null, null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage(MESSAGGIO_SLOT_OCCUPATO);
        }

        @Test
        void altraViolazioneDiIntegritaVieneRilanciata() {
            stubCampo(campoDiurno);
            stubNessunaPartitaAttiva(domani);
            stubUtenti(TEL_1);
            DataIntegrityViolationException altra = new DataIntegrityViolationException(
                    "could not execute statement",
                    new SQLIntegrityConstraintViolationException("Cannot add or update a child row: fk_partita_campo"));
            doThrow(altra).when(partitaRepository).save(any(Partita.class));

            assertThatThrownBy(() -> service.create(richiesta(domani, LocalTime.of(10, 0), null, TEL_1, null, null, null)))
                    .isSameAs(altra);
        }

        @Test
        void telefonoRipetutoDa409() {
            stubCampo(campoDiurno);
            stubNessunaPartitaAttiva(domani);
            stubUtenti(TEL_1);

            assertThatThrownBy(() -> service.create(richiesta(domani, LocalTime.of(10, 0), null, TEL_1, TEL_1, null, null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Lo stesso giocatore non può essere indicato più di una volta");
            verify(partitaRepository, never()).save(any());
        }

        @Test
        void telefonoRipetutoNonConsecutivoDa409() {
            stubCampo(campoDiurno);
            stubNessunaPartitaAttiva(domani);
            stubUtenti(TEL_1, TEL_2);

            assertThatThrownBy(() -> service.create(richiesta(domani, LocalTime.of(10, 0), null, TEL_1, TEL_2, null, TEL_2)))
                    .isInstanceOf(BusinessRuleException.class);
            verify(partitaRepository, never()).save(any());
        }

        @Test
        void campoInesistenteDa404() {
            when(campoService.trovaCampoOLancia(99)).thenThrow(new ResourceNotFoundException("Campo non trovato con id 99"));
            PrenotazioneDTO.CreateRequest req = richiesta(domani, LocalTime.of(10, 0), null, TEL_1, null, null, null);
            req.setIdCampo(99);

            assertThatThrownBy(() -> service.create(req)).isInstanceOf(ResourceNotFoundException.class);
            verify(partitaRepository, never()).save(any());
        }

        @Test
        void utenteInesistenteDa404() {
            stubCampo(campoDiurno);
            stubNessunaPartitaAttiva(domani);
            stubUtenti(TEL_1);
            when(utenteRepository.findById(TEL_ESTRANEO)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(richiesta(domani, LocalTime.of(10, 0), null, TEL_1, TEL_ESTRANEO, null, null)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(TEL_ESTRANEO);
            verify(partitaRepository, never()).save(any());
        }

        @Test
        void slotCheTerminaDopoLaChiusuraDa409() {
            stubCampo(campoDiurno);

            // 21:00 + 90' = 22:30 > chiusura 22:00
            assertThatThrownBy(() -> service.create(richiesta(domani, LocalTime.of(21, 0), null, TEL_1, null, null, null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("L'orario richiesto è fuori dalla fascia di apertura del campo");
        }

        @Test
        void slotCheTerminaEsattamenteAllaChiusuraEValido() {
            stubCampo(campoDiurno);
            stubNessunaPartitaAttiva(domani);
            stubUtenti(TEL_1);

            PrenotazioneDTO.Response r = service.create(richiesta(domani, LocalTime.of(20, 30), null, TEL_1, null, null, null));

            assertThat(r.getOraFine()).isEqualTo(LocalTime.of(22, 0));
        }

        @Test
        void slotPrimaDellAperturaDa409() {
            stubCampo(campoDiurno);

            assertThatThrownBy(() -> service.create(richiesta(domani, LocalTime.of(7, 0), null, TEL_1, null, null, null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("L'orario richiesto è fuori dalla fascia di apertura del campo");
        }

        @Test
        void campoCheChiudeDopoMezzanotteAccettaSlotACavalloDellaMezzanotte() {
            Campo serale = new Campo(2, "Campo Serale", false, LocalTime.of(18, 0), LocalTime.of(2, 0));
            when(campoService.trovaCampoOLancia(2)).thenReturn(serale);
            when(partitaRepository.findByCampoIdAndDataPartitaAndStatoNot(2, domani, StatoPartita.ANNULLATA))
                    .thenReturn(List.of());
            stubUtenti(TEL_1);
            PrenotazioneDTO.CreateRequest req = richiesta(domani, LocalTime.of(23, 30), null, TEL_1, null, null, null);
            req.setIdCampo(2);

            PrenotazioneDTO.Response r = service.create(req);

            assertThat(r.getOraFine()).isEqualTo(LocalTime.of(1, 0));
            assertThat(catturaPartitaSalvata().getOraFine()).isEqualTo(LocalTime.of(1, 0));
        }

        @Test
        void campoCheChiudeDopoMezzanotteAccettaUltimoSlotFinoAllaChiusura() {
            Campo serale = new Campo(2, "Campo Serale", false, LocalTime.of(18, 0), LocalTime.of(2, 0));
            when(campoService.trovaCampoOLancia(2)).thenReturn(serale);
            when(partitaRepository.findByCampoIdAndDataPartitaAndStatoNot(2, domani, StatoPartita.ANNULLATA))
                    .thenReturn(List.of());
            stubUtenti(TEL_1);
            PrenotazioneDTO.CreateRequest req = richiesta(domani, LocalTime.of(0, 30), null, TEL_1, null, null, null);
            req.setIdCampo(2);

            PrenotazioneDTO.Response r = service.create(req);

            assertThat(r.getOraFine()).isEqualTo(LocalTime.of(2, 0));
        }

        @Test
        void campoCheChiudeDopoMezzanotteRifiutaSlotOltreLaChiusura() {
            Campo serale = new Campo(2, "Campo Serale", false, LocalTime.of(18, 0), LocalTime.of(2, 0));
            when(campoService.trovaCampoOLancia(2)).thenReturn(serale);
            PrenotazioneDTO.CreateRequest req = richiesta(domani, LocalTime.of(1, 0), null, TEL_1, null, null, null);
            req.setIdCampo(2);

            // 01:00 + 90' = 02:30 > chiusura 02:00
            assertThatThrownBy(() -> service.create(req))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("L'orario richiesto è fuori dalla fascia di apertura del campo");
        }

        @Test
        void campoCheChiudeDopoMezzanotteRifiutaSlotPrimaDellApertura() {
            Campo serale = new Campo(2, "Campo Serale", false, LocalTime.of(18, 0), LocalTime.of(2, 0));
            when(campoService.trovaCampoOLancia(2)).thenReturn(serale);
            PrenotazioneDTO.CreateRequest req = richiesta(domani, LocalTime.of(10, 0), null, TEL_1, null, null, null);
            req.setIdCampo(2);

            assertThatThrownBy(() -> service.create(req))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("L'orario richiesto è fuori dalla fascia di apertura del campo");
        }

        @Test
        void campoCheChiudeDopoMezzanotteRilevaSovrapposizioneACavallo() {
            Campo serale = new Campo(2, "Campo Serale", false, LocalTime.of(18, 0), LocalTime.of(2, 0));
            when(campoService.trovaCampoOLancia(2)).thenReturn(serale);
            // esistente 23:30-01:00, nuova 00:30-02:00
            when(partitaRepository.findByCampoIdAndDataPartitaAndStatoNot(2, domani, StatoPartita.ANNULLATA))
                    .thenReturn(List.of(partita(1, serale, utente(TEL_2), domani, LocalTime.of(23, 30), StatoPartita.CREATA)));
            PrenotazioneDTO.CreateRequest req = richiesta(domani, LocalTime.of(0, 30), null, TEL_1, null, null, null);
            req.setIdCampo(2);

            assertThatThrownBy(() -> service.create(req))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage(MESSAGGIO_SLOT_OCCUPATO);
        }

        @Test
        void slotNelPassatoDa409() {
            stubCampo(campoDiurno);
            LocalDate ieri = LocalDate.now().minusDays(1);

            assertThatThrownBy(() -> service.create(richiesta(ieri, LocalTime.of(10, 0), null, TEL_1, null, null, null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Non è possibile prenotare uno slot nel passato");
            verify(partitaRepository, never()).save(any());
        }

        @Test
        void slotNelPassatoNonInterrogaIlRepository() {
            stubCampo(campoDiurno);
            LocalDate ieri = LocalDate.now().minusDays(1);

            assertThatThrownBy(() -> service.create(richiesta(ieri, LocalTime.of(10, 0), null, TEL_1, null, null, null)))
                    .isInstanceOf(BusinessRuleException.class);
            verify(utenteRepository, never()).findById(anyString());
        }
    }

    // ============================================
    // Response
    // ============================================
    @Nested
    class Risposta {

        @Test
        void giocatore1EIlCreatoreAncheSeNonEPrimaPartecipazione() {
            when(utenteRepository.existsByTelefono(TEL_2)).thenReturn(true);
            Partita p = partita(7, campoDiurno, utente(TEL_1), domani, LocalTime.of(10, 0), StatoPartita.CONFERMATA);
            // il creatore compare dopo un altro giocatore nell'elenco delle partecipazioni
            p.aggiungiPartecipazione(new Partecipazione(TipoPartecipazione.UTENTE, utente(TEL_2), false, true));
            p.aggiungiPartecipazione(new Partecipazione(TipoPartecipazione.UTENTE, utente(TEL_1), false, true));
            p.aggiungiPartecipazione(new Partecipazione(TipoPartecipazione.UTENTE, utente(TEL_3), true, false));
            when(partitaRepository.findByPartecipanteTelefono(TEL_2, TipoPartecipazione.UTENTE, StatoPartita.ANNULLATA))
                    .thenReturn(List.of(p));

            PrenotazioneDTO.Response r = service.getByUtente(TEL_2).get(0);

            assertThat(r.getTelefonoGiocatore1()).isEqualTo(TEL_1);
            assertThat(r.getTelefonoGiocatore2()).isEqualTo(TEL_2);
            assertThat(r.getTelefonoGiocatore3()).isEqualTo(TEL_3);
            assertThat(r.getTelefonoGiocatore4()).isNull();
            assertThat(r.getStato()).isEqualTo("confermata");
        }

        @Test
        void partecipazioniDiTipoCampoVengonoIgnorate() {
            when(utenteRepository.existsByTelefono(TEL_1)).thenReturn(true);
            Partita p = partita(7, campoDiurno, utente(TEL_1), domani, LocalTime.of(10, 0), StatoPartita.CREATA);
            p.aggiungiPartecipazione(new Partecipazione(TipoPartecipazione.UTENTE, utente(TEL_1), false, false));
            p.aggiungiPartecipazione(new Partecipazione(TipoPartecipazione.CAMPO, null, false, false));
            p.aggiungiPartecipazione(new Partecipazione(TipoPartecipazione.UTENTE, utente(TEL_4), false, false));
            when(partitaRepository.findByPartecipanteTelefono(TEL_1, TipoPartecipazione.UTENTE, StatoPartita.ANNULLATA))
                    .thenReturn(List.of(p));

            PrenotazioneDTO.Response r = service.getByUtente(TEL_1).get(0);

            assertThat(r.getTelefonoGiocatore1()).isEqualTo(TEL_1);
            assertThat(r.getTelefonoGiocatore2()).isEqualTo(TEL_4);
            assertThat(r.getTelefonoGiocatore3()).isNull();
            assertThat(r.getTelefonoGiocatore4()).isNull();
        }

        @Test
        void statoInMinuscoloPerOgniValore() {
            when(utenteRepository.existsByTelefono(TEL_1)).thenReturn(true);
            List<Partita> partite = new java.util.ArrayList<>();
            for (StatoPartita s : StatoPartita.values()) {
                Partita p = partita(s.ordinal(), campoDiurno, utente(TEL_1), domani, LocalTime.of(10, 0), s);
                p.aggiungiPartecipazione(new Partecipazione(TipoPartecipazione.UTENTE, utente(TEL_1), false, false));
                partite.add(p);
            }
            when(partitaRepository.findByPartecipanteTelefono(TEL_1, TipoPartecipazione.UTENTE, StatoPartita.ANNULLATA))
                    .thenReturn(partite);

            assertThat(service.getByUtente(TEL_1)).extracting(PrenotazioneDTO.Response::getStato)
                    .containsExactly("creata", "confermata", "annullata", "giocata");
        }
    }

    // ============================================
    // Cancellazione
    // ============================================
    @Nested
    class Cancellazione {

        @Test
        void partitaInesistenteDa404() {
            when(partitaRepository.findById(5)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancel(5, TEL_1, true))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void utenteNeCreatoreNeAdminDa409() {
            Partita p = partitaTraOre(48, StatoPartita.CREATA);
            when(partitaRepository.findById(5)).thenReturn(Optional.of(p));

            assertThatThrownBy(() -> service.cancel(5, TEL_ESTRANEO, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Non hai i permessi per cancellare questa prenotazione");
            assertThat(p.getStato()).isEqualTo(StatoPartita.CREATA);
        }

        @Test
        void partecipanteNonCreatoreNonPuoCancellare() {
            Partita p = partitaTraOre(48, StatoPartita.CREATA);
            p.aggiungiPartecipazione(new Partecipazione(TipoPartecipazione.UTENTE, utente(TEL_2), false, false));
            when(partitaRepository.findById(5)).thenReturn(Optional.of(p));

            assertThatThrownBy(() -> service.cancel(5, TEL_2, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Non hai i permessi per cancellare questa prenotazione");
        }

        @Test
        void permessiControllatiPrimaDelloStatoAnnullato() {
            Partita p = partitaTraOre(48, StatoPartita.ANNULLATA);
            when(partitaRepository.findById(5)).thenReturn(Optional.of(p));

            assertThatThrownBy(() -> service.cancel(5, TEL_ESTRANEO, false))
                    .hasMessage("Non hai i permessi per cancellare questa prenotazione");
        }

        @Test
        void giaAnnullataDa409() {
            when(partitaRepository.findById(5)).thenReturn(Optional.of(partitaTraOre(48, StatoPartita.ANNULLATA)));

            assertThatThrownBy(() -> service.cancel(5, TEL_1, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("La prenotazione è già stata annullata");
        }

        @Test
        void giaAnnullataControllataPrimaDelLimite24Ore() {
            when(partitaRepository.findById(5)).thenReturn(Optional.of(partitaTraOre(2, StatoPartita.ANNULLATA)));

            assertThatThrownBy(() -> service.cancel(5, TEL_1, false))
                    .hasMessage("La prenotazione è già stata annullata");
        }

        @Test
        void giaGiocataDa409() {
            when(partitaRepository.findById(5)).thenReturn(Optional.of(partitaTraOre(48, StatoPartita.GIOCATA)));

            assertThatThrownBy(() -> service.cancel(5, TEL_1, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Non è possibile annullare una partita già giocata");
        }

        @Test
        void giaGiocataDa409AncheAdAdmin() {
            Partita p = partitaTraOre(-48, StatoPartita.GIOCATA);
            when(partitaRepository.findById(5)).thenReturn(Optional.of(p));

            assertThatThrownBy(() -> service.cancel(5, TEL_ESTRANEO, true))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Non è possibile annullare una partita già giocata");
            assertThat(p.getStato()).isEqualTo(StatoPartita.GIOCATA);
        }

        @Test
        void creatoreSottoLe24OreDa409() {
            Partita p = partitaTraOre(23, StatoPartita.CREATA);
            when(partitaRepository.findById(5)).thenReturn(Optional.of(p));

            assertThatThrownBy(() -> service.cancel(5, TEL_1, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Non è possibile cancellare una prenotazione a meno di 24 ore dall'inizio");
            assertThat(p.getStato()).isEqualTo(StatoPartita.CREATA);
        }

        @Test
        void creatoreSopraLe24OrePuoAnnullare() {
            Partita p = partitaTraOre(25, StatoPartita.CREATA);
            when(partitaRepository.findById(5)).thenReturn(Optional.of(p));

            service.cancel(5, TEL_1, false);

            assertThat(p.getStato()).isEqualTo(StatoPartita.ANNULLATA);
        }

        @Test
        void creatorePuoAnnullarePartitaConfermata() {
            Partita p = partitaTraOre(72, StatoPartita.CONFERMATA);
            when(partitaRepository.findById(5)).thenReturn(Optional.of(p));

            service.cancel(5, TEL_1, false);

            assertThat(p.getStato()).isEqualTo(StatoPartita.ANNULLATA);
        }

        @Test
        void adminPuoAnnullareSottoLe24Ore() {
            Partita p = partitaTraOre(1, StatoPartita.CREATA);
            when(partitaRepository.findById(5)).thenReturn(Optional.of(p));

            service.cancel(5, TEL_ESTRANEO, true);

            assertThat(p.getStato()).isEqualTo(StatoPartita.ANNULLATA);
        }

        @Test
        void annullamentoNonCancellaLaRiga() {
            Partita p = partitaTraOre(48, StatoPartita.CREATA);
            when(partitaRepository.findById(5)).thenReturn(Optional.of(p));

            service.cancel(5, TEL_1, false);

            assertThat(p.getStato()).isEqualTo(StatoPartita.ANNULLATA);
            verify(partitaRepository, never()).delete(any());
            verify(partitaRepository, never()).deleteById(any());
        }
    }

    // ============================================
    // Slot disponibili
    // ============================================
    @Nested
    class SlotDisponibili {

        @Test
        void generaSlotDa90MinutiNellaFasciaDiApertura() {
            stubCampo(campoDiurno);
            stubNessunaPartitaAttiva(domani);

            List<PrenotazioneDTO.SlotResponse> slots = service.getSlotDisponibili(ID_CAMPO, domani);

            // 08:00-22:00 = 840 minuti = 9 slot da 90
            assertThat(slots).hasSize(9);
            assertThat(slots.get(0).getOraInizio()).isEqualTo(LocalTime.of(8, 0));
            assertThat(slots.get(0).getOraFine()).isEqualTo(LocalTime.of(9, 30));
            assertThat(slots.get(8).getOraInizio()).isEqualTo(LocalTime.of(20, 0));
            assertThat(slots.get(8).getOraFine()).isEqualTo(LocalTime.of(21, 30));
            assertThat(slots).allSatisfy(s -> {
                assertThat(s.getDisponibile()).isTrue();
                assertThat(s.getIdCampo()).isEqualTo(ID_CAMPO);
                assertThat(s.getNomeCampo()).isEqualTo("Campo Centrale");
            });
        }

        @Test
        void slotOccupatiDaPartiteAttiveNonSonoDisponibili() {
            stubCampo(campoDiurno);
            // 10:00-11:30 si sovrappone agli slot 09:30-11:00 e 11:00-12:30
            when(partitaRepository.findByCampoIdAndDataPartitaAndStatoNot(ID_CAMPO, domani, StatoPartita.ANNULLATA))
                    .thenReturn(List.of(partita(1, campoDiurno, utente(TEL_1), domani, LocalTime.of(10, 0), StatoPartita.CREATA)));

            List<PrenotazioneDTO.SlotResponse> slots = service.getSlotDisponibili(ID_CAMPO, domani);

            assertThat(slots).filteredOn(s -> !s.getDisponibile())
                    .extracting(PrenotazioneDTO.SlotResponse::getOraInizio)
                    .containsExactly(LocalTime.of(9, 30), LocalTime.of(11, 0));
        }

        @Test
        void partiteAnnullateNonOccupanoLoSlot() {
            stubCampo(campoDiurno);
            stubNessunaPartitaAttiva(domani);

            List<PrenotazioneDTO.SlotResponse> slots = service.getSlotDisponibili(ID_CAMPO, domani);

            // il service chiede al repository solo le partite con stato diverso da ANNULLATA
            verify(partitaRepository).findByCampoIdAndDataPartitaAndStatoNot(ID_CAMPO, domani, StatoPartita.ANNULLATA);
            verify(partitaRepository, never()).findAll();
            assertThat(slots).allMatch(PrenotazioneDTO.SlotResponse::getDisponibile);
        }

        @Test
        void campoCheChiudeDopoMezzanotteGeneraSlotACavallo() {
            Campo serale = new Campo(2, "Campo Serale", false, LocalTime.of(18, 0), LocalTime.of(2, 0));
            when(campoService.trovaCampoOLancia(2)).thenReturn(serale);
            when(partitaRepository.findByCampoIdAndDataPartitaAndStatoNot(2, domani, StatoPartita.ANNULLATA))
                    .thenReturn(List.of(partita(1, serale, utente(TEL_1), domani, LocalTime.of(0, 0), StatoPartita.CREATA)));

            List<PrenotazioneDTO.SlotResponse> slots = service.getSlotDisponibili(2, domani);

            // 18:00-02:00 = 480 minuti = 5 slot
            assertThat(slots).extracting(PrenotazioneDTO.SlotResponse::getOraInizio).containsExactly(
                    LocalTime.of(18, 0), LocalTime.of(19, 30), LocalTime.of(21, 0),
                    LocalTime.of(22, 30), LocalTime.of(0, 0));
            // 00:00-01:30 tocca 22:30-00:00 senza sovrapporsi: occupa solo lo slot delle 00:00
            assertThat(slots).filteredOn(s -> !s.getDisponibile())
                    .extracting(PrenotazioneDTO.SlotResponse::getOraInizio)
                    .containsExactly(LocalTime.of(0, 0));
        }

        @Test
        void campoInesistenteDa404() {
            when(campoService.trovaCampoOLancia(99)).thenThrow(new ResourceNotFoundException("Campo non trovato con id 99"));

            assertThatThrownBy(() -> service.getSlotDisponibili(99, domani))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ============================================
    // Prossima partita e storico utente
    // ============================================
    @Nested
    class ProssimaEStorico {

        @Test
        void prossimaUtenteInesistenteDa404() {
            when(utenteRepository.existsByTelefono(TEL_ESTRANEO)).thenReturn(false);

            assertThatThrownBy(() -> service.getProssimaByTelefono(TEL_ESTRANEO))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Utente non trovato con telefono " + TEL_ESTRANEO);
        }

        @Test
        void prossimaAssenteDa404() {
            when(utenteRepository.existsByTelefono(TEL_1)).thenReturn(true);
            when(partitaRepository.findProssimeByPartecipanteTelefono(eq(TEL_1), eq(TipoPartecipazione.UTENTE),
                    eq(StatoPartita.ANNULLATA), any(LocalDate.class), any(LocalTime.class))).thenReturn(List.of());

            assertThatThrownBy(() -> service.getProssimaByTelefono(TEL_1))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Nessuna prenotazione futura trovata per il telefono " + TEL_1);
        }

        @Test
        void prossimaRestituisceLaPrimaPartita() {
            when(utenteRepository.existsByTelefono(TEL_1)).thenReturn(true);
            Partita prima = partita(1, campoDiurno, utente(TEL_1), domani, LocalTime.of(10, 0), StatoPartita.CREATA);
            prima.aggiungiPartecipazione(new Partecipazione(TipoPartecipazione.UTENTE, utente(TEL_1), false, false));
            Partita seconda = partita(2, campoDiurno, utente(TEL_1), domani.plusDays(1), LocalTime.of(10, 0), StatoPartita.CREATA);
            seconda.aggiungiPartecipazione(new Partecipazione(TipoPartecipazione.UTENTE, utente(TEL_1), false, false));
            when(partitaRepository.findProssimeByPartecipanteTelefono(eq(TEL_1), eq(TipoPartecipazione.UTENTE),
                    eq(StatoPartita.ANNULLATA), any(LocalDate.class), any(LocalTime.class)))
                    .thenReturn(List.of(prima, seconda));

            assertThat(service.getProssimaByTelefono(TEL_1).getId()).isEqualTo(1);
        }

        @Test
        void storicoUtenteInesistenteDa404() {
            when(utenteRepository.existsByTelefono(TEL_ESTRANEO)).thenReturn(false);

            assertThatThrownBy(() -> service.getByUtente(TEL_ESTRANEO))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Utente non trovato con telefono " + TEL_ESTRANEO);
        }

        @Test
        void storicoEscludeLeAnnullate() {
            when(utenteRepository.existsByTelefono(TEL_1)).thenReturn(true);
            when(partitaRepository.findByPartecipanteTelefono(TEL_1, TipoPartecipazione.UTENTE, StatoPartita.ANNULLATA))
                    .thenReturn(List.of());

            assertThat(service.getByUtente(TEL_1)).isEmpty();
            verify(partitaRepository).findByPartecipanteTelefono(TEL_1, TipoPartecipazione.UTENTE, StatoPartita.ANNULLATA);
        }
    }

    // ============================================
    // Inizio effettivo (data_partita = data della serata)
    // ============================================
    // Il service usa LocalDateTime.now() senza Clock: i casi sono costruiti con date relative
    // ad adesso e con orari scelti perché l'esito non dipenda dall'ora in cui girano i test.
    @Nested
    class InizioEffettivo {

        private Campo serale;
        private LocalDate oggi;

        @BeforeEach
        void setUpSerale() {
            serale = new Campo(2, "Campo Serale", false, LocalTime.of(18, 0), LocalTime.of(2, 0));
            oggi = LocalDate.now();
        }

        // --- POST ---

        @Test
        void postAlle0030DellaSerataDiOggiEAccettataAncheDopoLe0030DiOggi() {
            // Inizio reale = domani 00:30, sempre nel futuro. Con il vecchio calcolo (oggi 00:30)
            // veniva rifiutata ogni volta che i test giravano dopo le 00:30.
            when(campoService.trovaCampoOLancia(2)).thenReturn(serale);
            when(partitaRepository.findByCampoIdAndDataPartitaAndStatoNot(2, oggi, StatoPartita.ANNULLATA))
                    .thenReturn(List.of());
            stubUtenti(TEL_1);
            org.mockito.Mockito.lenient().when(partitaRepository.save(any(Partita.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            PrenotazioneDTO.CreateRequest req = richiesta(oggi, LocalTime.of(0, 30), null, TEL_1, null, null, null);
            req.setIdCampo(2);

            PrenotazioneDTO.Response r = service.create(req);

            assertThat(r.getDataPrenotazione()).isEqualTo(oggi);
            assertThat(r.getOraInizio()).isEqualTo(LocalTime.of(0, 30));
            assertThat(catturaPartitaSalvata().getDataPartita()).isEqualTo(oggi);
        }

        @Test
        void postDopoMezzanotteDellaSerataDiIeriGiaPassataDa409() {
            // Serata di ieri, slot 00:00: inizio reale = oggi 00:00, già passato a qualunque ora.
            // Si usa 00:00 invece di 00:30 perché tra le 00:00 e le 00:30 lo slot delle 00:30
            // sarebbe ancora futuro e il test diventerebbe instabile.
            when(campoService.trovaCampoOLancia(2)).thenReturn(serale);
            PrenotazioneDTO.CreateRequest req = richiesta(oggi.minusDays(1), LocalTime.of(0, 0), null, TEL_1, null, null, null);
            req.setIdCampo(2);

            assertThatThrownBy(() -> service.create(req))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Non è possibile prenotare uno slot nel passato");
            verify(partitaRepository, never()).save(any());
        }

        @Test
        void postCampoNormaleNelPassatoResta409() {
            stubCampo(campoDiurno);

            assertThatThrownBy(() -> service.create(richiesta(oggi.minusDays(1), LocalTime.of(20, 30), null, TEL_1, null, null, null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Non è possibile prenotare uno slot nel passato");
        }

        // --- cancel ---

        @Test
        void cancelSottoLe24OreSullInizioRealeDa409() {
            // inizio reale tra ~23h30: data_partita è il giorno prima dell'inizio reale
            Partita p = partitaDopoMezzanotteCheIniziaTra(23 * 60 + 30);
            when(partitaRepository.findById(5)).thenReturn(Optional.of(p));

            assertThatThrownBy(() -> service.cancel(5, TEL_1, false))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Non è possibile cancellare una prenotazione a meno di 24 ore dall'inizio");
            assertThat(p.getStato()).isEqualTo(StatoPartita.CREATA);
        }

        @Test
        void cancelAppenaSopraLe24OreSullInizioRealeRiesce() {
            // inizio reale tra ~24h30, ma data_partita + ora è solo ~30 minuti da adesso:
            // con il vecchio calcolo sarebbe stata rifiutata
            Partita p = partitaDopoMezzanotteCheIniziaTra(24 * 60 + 30);
            assertThat(LocalDateTime.of(p.getDataPartita(), p.getOraInizio())).isBefore(LocalDateTime.now().plusHours(1));
            when(partitaRepository.findById(5)).thenReturn(Optional.of(p));

            service.cancel(5, TEL_1, false);

            assertThat(p.getStato()).isEqualTo(StatoPartita.ANNULLATA);
        }

        @Test
        void cancelBenOltreLe24OreSullInizioRealeRiesce() {
            // Campo 18:00-02:00, serata di domani, slot 00:30: inizio reale dopodomani 00:30,
            // quindi tra 24h30 e 48h30 a qualunque ora
            Partita p = partita(5, serale, utente(TEL_1), oggi.plusDays(1), LocalTime.of(0, 30), StatoPartita.CREATA);
            when(partitaRepository.findById(5)).thenReturn(Optional.of(p));

            service.cancel(5, TEL_1, false);

            assertThat(p.getStato()).isEqualTo(StatoPartita.ANNULLATA);
        }

        @Test
        void cancelCampoNormaleNonSpostaIlGiorno() {
            // Campo 08:00-22:00 con data/ora del giorno stesso tra ~23h30: resta sotto le 24 ore
            LocalDateTime inizio = LocalDateTime.now().plusMinutes(23 * 60 + 30);
            Partita p = partita(5, campoDiurno, utente(TEL_1), inizio.toLocalDate(), inizio.toLocalTime(), StatoPartita.CREATA);
            when(partitaRepository.findById(5)).thenReturn(Optional.of(p));

            assertThatThrownBy(() -> service.cancel(5, TEL_1, false))
                    .isInstanceOf(BusinessRuleException.class);
        }

        // --- prossima ---

        @Test
        void prossimaCaricaLeCandidateDaIeriAlle0000() {
            when(utenteRepository.existsByTelefono(TEL_1)).thenReturn(true);
            LocalDate ieriPrima = LocalDate.now().minusDays(1);
            stubProssime(List.of(partitaUtente(1, serale, oggi, LocalTime.of(0, 30))));

            service.getProssimaByTelefono(TEL_1);

            ArgumentCaptor<LocalDate> data = ArgumentCaptor.forClass(LocalDate.class);
            ArgumentCaptor<LocalTime> ora = ArgumentCaptor.forClass(LocalTime.class);
            verify(partitaRepository).findProssimeByPartecipanteTelefono(eq(TEL_1), eq(TipoPartecipazione.UTENTE),
                    eq(StatoPartita.ANNULLATA), data.capture(), ora.capture());
            assertThat(data.getValue()).isIn(ieriPrima, LocalDate.now().minusDays(1));
            assertThat(ora.getValue()).isEqualTo(LocalTime.MIN);
        }

        @Test
        void prossimaConsideraFuturaLaPartitaDelle0030DiStasera() {
            when(utenteRepository.existsByTelefono(TEL_1)).thenReturn(true);
            // ieri 00:00 della serata di ieri (inizio reale oggi 00:00): già passata
            Partita passata = partitaUtente(1, serale, oggi.minusDays(1), LocalTime.of(0, 0));
            // serata di oggi, 00:30: inizio reale domani 00:30
            Partita stasera = partitaUtente(2, serale, oggi, LocalTime.of(0, 30));
            stubProssime(List.of(passata, stasera));

            assertThat(service.getProssimaByTelefono(TEL_1).getId()).isEqualTo(2);
        }

        @Test
        void prossimaEscludeLaPartitaDopoMezzanotteDiIeriSeraGiaPassata() {
            when(utenteRepository.existsByTelefono(TEL_1)).thenReturn(true);
            // vale lo stesso discorso di stabilità del test POST: 00:00 invece di 00:30
            stubProssime(List.of(partitaUtente(1, serale, oggi.minusDays(1), LocalTime.of(0, 0))));

            assertThatThrownBy(() -> service.getProssimaByTelefono(TEL_1))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Nessuna prenotazione futura trovata per il telefono " + TEL_1);
        }

        @Test
        void prossimaFra0030E2100DellaStessaSerataVinceLe2100() {
            when(utenteRepository.existsByTelefono(TEL_1)).thenReturn(true);
            LocalDate domaniSera = oggi.plusDays(1);
            // ordine della query (data_partita, ora_inizio ASC): la 00:30 arriva prima della 21:00
            Partita dopoMezzanotte = partitaUtente(1, serale, domaniSera, LocalTime.of(0, 30));
            Partita alle21 = partitaUtente(2, serale, domaniSera, LocalTime.of(21, 0));
            stubProssime(List.of(dopoMezzanotte, alle21));

            assertThat(service.getProssimaByTelefono(TEL_1).getId()).isEqualTo(2);
        }

        @Test
        void prossimaCampoNormaleSceglieLaPrimaFutura() {
            when(utenteRepository.existsByTelefono(TEL_1)).thenReturn(true);
            Partita ieri = partitaUtente(1, campoDiurno, oggi.minusDays(1), LocalTime.of(10, 0));
            Partita domani = partitaUtente(2, campoDiurno, oggi.plusDays(1), LocalTime.of(8, 0));
            Partita dopodomani = partitaUtente(3, campoDiurno, oggi.plusDays(2), LocalTime.of(8, 0));
            stubProssime(List.of(ieri, domani, dopodomani));

            assertThat(service.getProssimaByTelefono(TEL_1).getId()).isEqualTo(2);
        }

        // --- storico ---

        @Test
        void storicoOrdinatoPerInizioRealeDecrescente() {
            when(utenteRepository.existsByTelefono(TEL_1)).thenReturn(true);
            LocalDate d = oggi.minusDays(3);
            Partita alle21 = partitaUtente(1, serale, d, LocalTime.of(21, 0));
            Partita alle23 = partitaUtente(2, serale, d, LocalTime.of(23, 0));
            Partita alle0030 = partitaUtente(3, serale, d, LocalTime.of(0, 30));
            Partita seraPrima = partitaUtente(4, serale, d.minusDays(1), LocalTime.of(0, 0));
            // ordine della query (data_partita DESC, ora_inizio DESC)
            when(partitaRepository.findByPartecipanteTelefono(TEL_1, TipoPartecipazione.UTENTE, StatoPartita.ANNULLATA))
                    .thenReturn(List.of(alle23, alle21, alle0030, seraPrima));

            assertThat(service.getByUtente(TEL_1)).extracting(PrenotazioneDTO.Response::getId)
                    .containsExactly(3, 2, 1, 4);
        }

        @Test
        void storicoCampoNormaleOrdinatoPerDataEOraDecrescenti() {
            when(utenteRepository.existsByTelefono(TEL_1)).thenReturn(true);
            Partita a = partitaUtente(1, campoDiurno, oggi.minusDays(2), LocalTime.of(8, 0));
            Partita b = partitaUtente(2, campoDiurno, oggi.minusDays(2), LocalTime.of(20, 0));
            Partita c = partitaUtente(3, campoDiurno, oggi.minusDays(1), LocalTime.of(9, 30));
            when(partitaRepository.findByPartecipanteTelefono(TEL_1, TipoPartecipazione.UTENTE, StatoPartita.ANNULLATA))
                    .thenReturn(List.of(a, c, b));

            assertThat(service.getByUtente(TEL_1)).extracting(PrenotazioneDTO.Response::getId)
                    .containsExactly(3, 2, 1);
        }

        // --- helper ---

        private void stubProssime(List<Partita> candidate) {
            when(partitaRepository.findProssimeByPartecipanteTelefono(eq(TEL_1), eq(TipoPartecipazione.UTENTE),
                    eq(StatoPartita.ANNULLATA), any(LocalDate.class), any(LocalTime.class))).thenReturn(candidate);
        }

        private Partita partitaUtente(Integer id, Campo campo, LocalDate data, LocalTime inizio) {
            Partita p = partita(id, campo, utente(TEL_1), data, inizio, StatoPartita.CREATA);
            p.aggiungiPartecipazione(new Partecipazione(TipoPartecipazione.UTENTE, utente(TEL_1), false, false));
            return p;
        }

        // Partita "dopo mezzanotte" del creatore TEL_1 con inizio reale tra circa i minuti indicati
        // (arrotondato per difetto al minuto). Per poter scegliere l'istante liberamente si usa un campo
        // aperto 24 ore con apertura = chiusura = 23:59: ogni orario prima delle 23:59 appartiene
        // alla parte dopo mezzanotte, quindi data_partita è il giorno prima dell'inizio reale.
        private Partita partitaDopoMezzanotteCheIniziaTra(long minuti) {
            LocalDateTime inizioReale = LocalDateTime.now().plusMinutes(minuti).withSecond(0).withNano(0);
            if (!inizioReale.toLocalTime().isBefore(LocalTime.of(23, 59))) {
                // 23:59 non è "prima dell'apertura": si anticipa di un minuto, le ore intere non cambiano
                inizioReale = inizioReale.minusMinutes(1);
            }
            Campo h24 = new Campo(3, "Campo H24", true, LocalTime.of(23, 59), LocalTime.of(23, 59));
            Partita p = partita(5, h24, utente(TEL_1), inizioReale.toLocalDate().minusDays(1),
                    inizioReale.toLocalTime(), StatoPartita.CREATA);
            p.aggiungiPartecipazione(new Partecipazione(TipoPartecipazione.UTENTE, utente(TEL_1), false, false));
            return p;
        }
    }

    // ============================================
    // Helper
    // ============================================
    private void stubCampo(Campo campo) {
        when(campoService.trovaCampoOLancia(campo.getId())).thenReturn(campo);
    }

    private void stubNessunaPartitaAttiva(LocalDate data) {
        when(partitaRepository.findByCampoIdAndDataPartitaAndStatoNot(ID_CAMPO, data, StatoPartita.ANNULLATA))
                .thenReturn(List.of());
    }

    private void stubUtenti(String... telefoni) {
        for (String t : telefoni) {
            when(utenteRepository.findById(t)).thenReturn(Optional.of(utente(t)));
        }
    }

    private Partita catturaPartitaSalvata() {
        ArgumentCaptor<Partita> captor = ArgumentCaptor.forClass(Partita.class);
        verify(partitaRepository).save(captor.capture());
        return captor.getValue();
    }

    private static Utente utente(String telefono) {
        return new Utente(telefono, "Nome", "Cognome", "hash", LocalDate.of(1990, 1, 1), false);
    }

    private static Partita partita(Integer id, Campo campo, Utente creatore, LocalDate data, LocalTime inizio, StatoPartita stato) {
        Partita p = new Partita();
        p.setId(id);
        p.setCampo(campo);
        p.setCreatore(creatore);
        p.setDataPartita(data);
        p.setOraInizio(inizio);
        p.setOraFine(inizio.plusMinutes(90));
        p.setTipo(TipoPartita.PRIVATA);
        p.setStato(stato);
        return p;
    }

    // Partita del creatore TEL_1 che inizia tra il numero di ore indicato (negativo = nel passato)
    private Partita partitaTraOre(long ore, StatoPartita stato) {
        LocalDateTime inizio = LocalDateTime.now().plusHours(ore).plusMinutes(5);
        Partita p = partita(5, campoDiurno, utente(TEL_1), inizio.toLocalDate(), inizio.toLocalTime(), stato);
        p.aggiungiPartecipazione(new Partecipazione(TipoPartecipazione.UTENTE, utente(TEL_1), false, false));
        return p;
    }

    private static PrenotazioneDTO.CreateRequest richiesta(LocalDate data, LocalTime inizio, String tipo,
                                                          String g1, String g2, String g3, String g4) {
        PrenotazioneDTO.CreateRequest r = new PrenotazioneDTO.CreateRequest();
        r.setIdCampo(ID_CAMPO);
        r.setDataPrenotazione(data);
        r.setOraInizio(inizio);
        r.setTipo(tipo);
        r.setTelefonoGiocatore1(g1);
        r.setTelefonoGiocatore2(g2);
        r.setTelefonoGiocatore3(g3);
        r.setTelefonoGiocatore4(g4);
        return r;
    }
}
