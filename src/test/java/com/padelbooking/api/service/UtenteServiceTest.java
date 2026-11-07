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
import com.padelbooking.api.repository.NotificaPushRepository;
import com.padelbooking.api.repository.PartitaRepository;
import com.padelbooking.api.repository.UtenteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UtenteServiceTest {

    private static final String TEL_RICHIEDENTE = "3330000001";
    private static final String TEL_ALTRO = "3330000002";
    private static final String TEL_ADMIN = "0000000000";

    private static final ZoneId ZONA = ZoneId.of("Europe/Rome");
    private static final LocalDateTime ADESSO = LocalDateTime.of(2026, 11, 7, 12, 0);
    private static final Campo CAMPO = new Campo(1, "Campo 1", true, LocalTime.of(8, 0), LocalTime.of(22, 0));

    @Mock
    private UtenteRepository utenteRepository;

    @Mock
    private PartitaRepository partitaRepository;

    @Mock
    private NotificaPushRepository notificaPushRepository;

    private UtenteService service;

    @BeforeEach
    void setUp() {
        service = new UtenteService(utenteRepository, partitaRepository, notificaPushRepository,
                Clock.fixed(ADESSO.atZone(ZONA).toInstant(), ZONA));
    }

    @Test
    void escludeChiChiamaEMantieneLOrdineDellaQuery() {
        when(utenteRepository.findNonAdmin("%%")).thenReturn(List.of(
                utente("3330000003", "Anna", "Bianchi"),
                utente(TEL_RICHIEDENTE, "Mario", "Rossi"),
                utente("3330000002", "Luigi", "Verdi")));

        List<PartitaDTO.InvitabileResponse> r = service.getInvitabili(null, TEL_RICHIEDENTE);

        assertThat(r).extracting(PartitaDTO.InvitabileResponse::getTelefono)
                .containsExactly("3330000003", "3330000002");
        assertThat(r.get(0).getCognome()).isEqualTo("Bianchi");
        assertThat(r.get(0).getLivello()).isEqualTo(2.0);
    }

    @Test
    void usaLaStessaRicercaDegliInvitabiliDellaPartita() {
        when(utenteRepository.findNonAdmin("%50!%%")).thenReturn(List.of());

        assertThat(service.getInvitabili("  50% ", TEL_RICHIEDENTE)).isEmpty();

        verify(utenteRepository).findNonAdmin("%50!%%");
    }

    // ============================================
    // Eliminazione dell'account (T18)
    // ============================================

    @Test
    void eliminaAccountUtenteInesistente404SenzaToccareNulla() {
        when(utenteRepository.findById(TEL_RICHIEDENTE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.eliminaAccount(TEL_RICHIEDENTE))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Utente non trovato");
        verify(utenteRepository, never()).delete(any());
    }

    @Test
    void eliminaAccountEscePassaLaPartitaAllAdminEAnonimizzaLoStorico() {
        Utente io = utente(TEL_RICHIEDENTE, "Mario", "Rossi");
        Utente altro = utente(TEL_ALTRO, "Luigi", "Verdi");
        Utente admin = new Utente(TEL_ADMIN, "Admin", "Padel", "x", LocalDate.of(2000, 1, 1), true);

        // Futura, creata da me con un altro giocatore: esco e passa all'admin
        Partita creata = partita(1, io, StatoPartita.CREATA, ADESSO.plusDays(3), io, altro);
        // Futura di un altro in cui sono l'unico utente con un giocatore campo: esco e viene annullata
        Partita soloIo = partita(2, admin, StatoPartita.CREATA, ADESSO.plusDays(4), io);
        soloIo.aggiungiPartecipazione(new Partecipazione(TipoPartecipazione.CAMPO, null, false, false));
        // Giocata, creata e registrata da me: il posto resta anonimo con squadra e punti
        Partita giocata = partita(3, io, StatoPartita.GIOCATA, ADESSO.minusDays(2), io, altro);
        giocata.setRegistrante(io);
        Partecipazione mioPosto = giocata.getPartecipazioni().get(0);
        mioPosto.setSquadra(1);
        mioPosto.setPuntiAssegnati(3);

        when(utenteRepository.findById(TEL_RICHIEDENTE)).thenReturn(Optional.of(io));
        when(utenteRepository.findAdminTranne(TEL_RICHIEDENTE)).thenReturn(List.of(admin));
        when(partitaRepository.findIdCollegateAUtente(TEL_RICHIEDENTE)).thenReturn(List.of(1, 2, 3));
        when(partitaRepository.findByIdPerAggiornamento(1)).thenReturn(Optional.of(creata));
        when(partitaRepository.findByIdPerAggiornamento(2)).thenReturn(Optional.of(soloIo));
        when(partitaRepository.findByIdPerAggiornamento(3)).thenReturn(Optional.of(giocata));

        service.eliminaAccount(TEL_RICHIEDENTE);

        assertThat(creata.getCreatore()).isSameAs(admin);
        assertThat(creata.getStato()).isEqualTo(StatoPartita.CREATA);
        assertThat(creata.getPartecipazioni()).extracting(pa -> pa.getUtente().getTelefono()).containsExactly(TEL_ALTRO);

        assertThat(soloIo.getStato()).isEqualTo(StatoPartita.ANNULLATA);
        assertThat(soloIo.getPartecipazioni()).extracting(Partecipazione::getTipo)
                .containsExactly(TipoPartecipazione.CAMPO);

        assertThat(giocata.getCreatore()).isNull();
        assertThat(giocata.getRegistrante()).isNull();
        assertThat(giocata.getPartecipazioni()).hasSize(2);
        assertThat(mioPosto.getUtente()).isNull();
        assertThat(mioPosto.getTipo()).isEqualTo(TipoPartecipazione.UTENTE);
        assertThat(mioPosto.getSquadra()).isEqualTo(1);
        assertThat(mioPosto.getPuntiAssegnati()).isEqualTo(3);

        // Prima le partite e le notifiche, scritte con il flush, poi la riga utente
        InOrder ordine = inOrder(notificaPushRepository, partitaRepository, utenteRepository);
        ordine.verify(notificaPushRepository).deleteByDestinatario(io);
        ordine.verify(partitaRepository).flush();
        ordine.verify(utenteRepository).delete(io);
    }

    @Test
    void eliminaAccountSenzaAltriAdminLasciaIlCreatoreVuoto() {
        Utente io = new Utente(TEL_ADMIN, "Admin", "Padel", "x", LocalDate.of(2000, 1, 1), true);
        Utente altro = utente(TEL_ALTRO, "Luigi", "Verdi");
        Partita futura = partita(1, io, StatoPartita.CREATA, ADESSO.plusDays(3), altro);

        when(utenteRepository.findById(TEL_ADMIN)).thenReturn(Optional.of(io));
        when(utenteRepository.findAdminTranne(TEL_ADMIN)).thenReturn(List.of());
        when(partitaRepository.findIdCollegateAUtente(TEL_ADMIN)).thenReturn(List.of(1));
        when(partitaRepository.findByIdPerAggiornamento(1)).thenReturn(Optional.of(futura));

        service.eliminaAccount(TEL_ADMIN);

        assertThat(futura.getCreatore()).isNull();
        assertThat(futura.getStato()).isEqualTo(StatoPartita.CREATA);
        assertThat(futura.getPartecipazioni()).hasSize(1);
        verify(utenteRepository).delete(io);
    }

    private static Partita partita(int id, Utente creatore, StatoPartita stato, LocalDateTime inizio,
                                   Utente... giocatori) {
        Partita p = new Partita();
        p.setId(id);
        p.setCampo(CAMPO);
        p.setCreatore(creatore);
        p.setStato(stato);
        p.setTipo(TipoPartita.PUBBLICA);
        p.setDataPartita(inizio.toLocalDate());
        p.setOraInizio(inizio.toLocalTime());
        p.setOraFine(inizio.toLocalTime().plusMinutes(90));
        for (Utente g : giocatori) {
            p.aggiungiPartecipazione(new Partecipazione(TipoPartecipazione.UTENTE, g, false, false));
        }
        return p;
    }

    private static Utente utente(String telefono, String nome, String cognome) {
        return new Utente(telefono, nome, cognome, "x", LocalDate.of(1990, 1, 1), false, 2.0);
    }
}
