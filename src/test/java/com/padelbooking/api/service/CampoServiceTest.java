package com.padelbooking.api.service;

import com.padelbooking.api.exception.BusinessRuleException;
import com.padelbooking.api.exception.ResourceNotFoundException;
import com.padelbooking.api.model.Campo;
import com.padelbooking.api.repository.CampoRepository;
import com.padelbooking.api.repository.PartitaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampoServiceTest {

    private static final String MESSAGGIO = "Il campo ha partite collegate e non può essere eliminato";

    @Mock
    private CampoRepository campoRepository;
    @Mock
    private PartitaRepository partitaRepository;
    @InjectMocks
    private CampoService service;

    private final Campo campo = new Campo(1, "Campo Centrale", true, LocalTime.of(8, 0), LocalTime.of(22, 0));

    @BeforeEach
    void setUp() {
        lenient().when(campoRepository.findById(1)).thenReturn(Optional.of(campo));
    }

    @Test
    void eliminaUnCampoSenzaPartite() {
        when(partitaRepository.existsByCampoId(1)).thenReturn(false);

        service.delete(1);

        var ordine = inOrder(campoRepository);
        ordine.verify(campoRepository).delete(campo);
        ordine.verify(campoRepository).flush();
    }

    @Test
    void conPartiteCollegate409() {
        when(partitaRepository.existsByCampoId(1)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(MESSAGGIO);
        verify(campoRepository, never()).delete(any());
    }

    @Test
    void partitaCreataInParallelo409() {
        when(partitaRepository.existsByCampoId(1)).thenReturn(false);
        doThrow(new DataIntegrityViolationException("a foreign key constraint fails (CONSTRAINT `fk_partita_campo`)"))
                .when(campoRepository).flush();

        assertThatThrownBy(() -> service.delete(1))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(MESSAGGIO);
    }

    @Test
    void campoInesistente404() {
        when(campoRepository.findById(9)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(9))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Campo non trovato con id 9");
    }
}
