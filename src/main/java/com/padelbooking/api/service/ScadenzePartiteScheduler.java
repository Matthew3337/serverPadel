package com.padelbooking.api.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Avvia ogni minuto il giro delle scadenze (RNF_O_2, RNF_A_1–2). Nessuna logica: le regole stanno
// in ScadenzePartiteService. Con padel.scadenze.attive=false il componente non viene creato.
@Component
@ConditionalOnProperty(name = "padel.scadenze.attive", havingValue = "true", matchIfMissing = true)
public class ScadenzePartiteScheduler {

    // RNF_A_1: l'annullamento deve avvenire entro il minuto dell'ora di inizio, quindi al più ogni 60 s
    private static final long PERIODO_MS = 60_000;

    private final ScadenzePartiteService scadenzePartiteService;

    public ScadenzePartiteScheduler(ScadenzePartiteService scadenzePartiteService) {
        this.scadenzePartiteService = scadenzePartiteService;
    }

    // fixedRate senza ritardo iniziale: il primo giro parte all'avvio e recupera subito le scadenze
    // maturate durante un fermo (RNF_A_3). Lo scheduler di Spring ha un solo thread, quindi un giro
    // lento fa slittare il successivo ma due giri non si sovrappongono.
    @Scheduled(fixedRate = PERIODO_MS)
    public void eseguiGiro() {
        scadenzePartiteService.eseguiGiro();
    }
}
