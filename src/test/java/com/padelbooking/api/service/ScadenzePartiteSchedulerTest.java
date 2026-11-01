package com.padelbooking.api.service;

import com.padelbooking.api.config.SchedulingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

// Verifica la proprietà padel.scadenze.attive su un contesto minimo, senza database:
// lo scheduler e @EnableScheduling ci sono di default e spariscono con false
class ScadenzePartiteSchedulerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulingConfig.class, ScadenzePartiteScheduler.class)
            .withBean(ScadenzePartiteService.class, () -> mock(ScadenzePartiteService.class));

    @Test
    void attivoDiDefault() {
        runner.run(contesto -> {
            assertThat(contesto).hasSingleBean(ScadenzePartiteScheduler.class);
            assertThat(contesto).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class);
        });
    }

    @Test
    void disattivatoConProprietaFalse() {
        runner.withPropertyValues("padel.scadenze.attive=false").run(contesto -> {
            assertThat(contesto).doesNotHaveBean(ScadenzePartiteScheduler.class);
            assertThat(contesto).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class);
        });
    }

    @Test
    void ilGiroDelegaAlService() {
        ScadenzePartiteService service = mock(ScadenzePartiteService.class);

        new ScadenzePartiteScheduler(service).eseguiGiro();

        verify(service).eseguiGiro();
    }
}
