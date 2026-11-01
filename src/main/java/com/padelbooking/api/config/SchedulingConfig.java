package com.padelbooking.api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Attiva le attività pianificate (ScadenzePartiteScheduler). Sta in una classe a parte e non su
// PadelBookingApiApplication: le annotazioni della classe principale valgono anche nei test
// @WebMvcTest, che così non avviano lo scheduler. Con padel.scadenze.attive=false non si attiva.
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "padel.scadenze.attive", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
