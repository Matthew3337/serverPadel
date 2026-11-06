package com.padelbooking.api.service;

import com.padelbooking.api.dto.PartitaDTO;
import com.padelbooking.api.model.Utente;
import com.padelbooking.api.repository.UtenteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UtenteServiceTest {

    private static final String TEL_RICHIEDENTE = "3330000001";

    @Mock
    private UtenteRepository utenteRepository;

    private UtenteService service;

    @BeforeEach
    void setUp() {
        service = new UtenteService(utenteRepository);
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

    private static Utente utente(String telefono, String nome, String cognome) {
        return new Utente(telefono, nome, cognome, "x", LocalDate.of(1990, 1, 1), false, 2.0);
    }
}
