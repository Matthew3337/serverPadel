package com.padelbooking.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

// Orologio da cui i service leggono "adesso": in produzione è quello di sistema (stesso fuso di
// LocalDateTime.now()), nei test si sostituisce con Clock.fixed per verificare le soglie temporali.
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
