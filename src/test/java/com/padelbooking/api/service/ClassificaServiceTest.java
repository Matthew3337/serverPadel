package com.padelbooking.api.service;

import com.padelbooking.api.dto.ClassificaDTO;
import com.padelbooking.api.model.Utente;
import com.padelbooking.api.repository.UtenteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassificaServiceTest {

    @Mock
    private UtenteRepository utenteRepository;

    private ClassificaService service;

    @BeforeEach
    void setUp() {
        service = new ClassificaService(utenteRepository);
    }

    @Test
    void chiHaGliStessiPuntiHaLaStessaPosizioneEPoiSiSalta() {
        when(utenteRepository.findClassifica()).thenReturn(List.of(
                utente("1", "Rossi", 9), utente("2", "Bianchi", 5), utente("3", "Verdi", 5), utente("4", "Neri", 1)));

        assertThat(service.getClassifica("1")).extracting(ClassificaDTO.Response::getPosizione)
                .containsExactly(1, 2, 2, 4);
    }

    @Test
    void tuttiAParitaSonoPrimiAncheA0Punti() {
        when(utenteRepository.findClassifica()).thenReturn(List.of(
                utente("1", "Bianchi", 0), utente("2", "Rossi", 0), utente("3", "Verdi", 0)));

        assertThat(service.getClassifica("1")).extracting(ClassificaDTO.Response::getPosizione)
                .containsExactly(1, 1, 1);
    }

    @Test
    void piuGruppiDiParitaInCoda() {
        when(utenteRepository.findClassifica()).thenReturn(List.of(
                utente("1", "A", 7), utente("2", "B", 7), utente("3", "C", 7),
                utente("4", "D", 3), utente("5", "E", 3), utente("6", "F", 1)));

        assertThat(service.getClassifica("1")).extracting(ClassificaDTO.Response::getPosizione)
                .containsExactly(1, 1, 1, 4, 4, 6);
    }

    @Test
    void portaITuttiICampiDelContratto() {
        when(utenteRepository.findClassifica()).thenReturn(List.of(utente("1", "Rossi", 9)));

        ClassificaDTO.Response riga = service.getClassifica("1").get(0);

        assertThat(riga.getPosizione()).isEqualTo(1);
        assertThat(riga.getTelefono()).isEqualTo("1");
        assertThat(riga.getNome()).isEqualTo("Nome");
        assertThat(riga.getCognome()).isEqualTo("Rossi");
        assertThat(riga.getPunteggio()).isEqualTo(9);
    }

    @Test
    void utenteCorrenteSoloSullaRigaDiChiChiama() {
        when(utenteRepository.findClassifica()).thenReturn(List.of(utente("1", "Rossi", 9), utente("2", "Bianchi", 5)));

        assertThat(service.getClassifica("2")).extracting(ClassificaDTO.Response::getUtenteCorrente)
                .containsExactly(false, true);
    }

    @Test
    void perUnAdminNonInElencoUtenteCorrenteESempreFalse() {
        when(utenteRepository.findClassifica()).thenReturn(List.of(utente("1", "Rossi", 9), utente("2", "Bianchi", 5)));

        assertThat(service.getClassifica("3330000100")).extracting(ClassificaDTO.Response::getUtenteCorrente)
                .containsExactly(false, false);
    }

    @Test
    void elencoVuoto() {
        when(utenteRepository.findClassifica()).thenReturn(List.of());

        assertThat(service.getClassifica("1")).isEmpty();
    }

    @Test
    void leggeIlPunteggioMemorizzatoSenzaAltreQuery() {
        // RNF_P_1: nessuna lettura dallo storico, il service dipende solo da UtenteRepository
        when(utenteRepository.findClassifica()).thenReturn(List.of(utente("1", "Rossi", 9)));

        service.getClassifica("1");

        verify(utenteRepository).findClassifica();
        verifyNoMoreInteractions(utenteRepository);
    }

    @Test
    void laQueryEscludeGliAdminEOrdinaComeDaContratto() throws Exception {
        // Esclusione e ordine stanno nella query: i test del service usano un mock, quindi si controlla
        // il testo qui e il comportamento reale sul MySQL temporaneo (niente H2, vedi CLAUDE.md)
        String query = UtenteRepository.class.getMethod("findClassifica").getAnnotation(Query.class).value();

        assertThat(query).contains("u.isAdmin = false")
                .contains("ORDER BY u.punteggio DESC, u.cognome ASC, u.nome ASC");
    }

    private static Utente utente(String telefono, String cognome, int punteggio) {
        Utente utente = new Utente(telefono, "Nome", cognome, "x", LocalDate.of(1990, 1, 1), false);
        utente.setPunteggio(punteggio);
        return utente;
    }
}
