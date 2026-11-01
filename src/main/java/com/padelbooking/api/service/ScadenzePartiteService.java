package com.padelbooking.api.service;

import com.padelbooking.api.model.Avviso;
import com.padelbooking.api.model.Partita;
import com.padelbooking.api.model.StatoPartita;
import com.padelbooking.api.model.TipoAvviso;
import com.padelbooking.api.repository.AvvisoRepository;
import com.padelbooking.api.repository.PartitaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// Scadenze automatiche delle partite (RNF_O_2, RF_GC_1–2, RF_GC_8–10, RF_GA_8), applicate a ogni giro
// da ScadenzePartiteScheduler. Ogni passo si basa su "scadenza <= adesso" e non sul minuto esatto,
// quindi dopo un fermo del server le scadenze maturate si recuperano al primo giro (RNF_A_3), ed è
// idempotente: ripetere un giro sulla stessa partita non cambia nulla.
@Service
public class ScadenzePartiteService {

    private static final Logger log = LoggerFactory.getLogger(ScadenzePartiteService.class);

    // Una scadenza è maturata da −24 h, cioè con inizio effettivo <= adesso + 24 h, al più domani.
    // L'inizio effettivo cade al più il giorno dopo data_partita, quindi bastano le partite con
    // data_partita fino a domani; dopodomani lascia un giorno di margine senza leggere molte righe.
    private static final int GIORNI_CANDIDATE = 2;

    // Nomi dei passi e degli esiti nei log, per seguire l'attività partita per partita
    private static final String PASSO_AVVISO = "-24h";
    private static final String PASSO_INIZIO = "INIZIO";

    private final PartitaRepository partitaRepository;
    private final AvvisoRepository avvisoRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public ScadenzePartiteService(PartitaRepository partitaRepository,
                                  AvvisoRepository avvisoRepository,
                                  PlatformTransactionManager transactionManager,
                                  Clock clock) {
        this.partitaRepository = partitaRepository;
        this.avvisoRepository = avvisoRepository;
        // Transazione esplicita per ogni partita invece di @Transactional su un metodo di questa classe:
        // chiamato dall'interno non passerebbe dal proxy di Spring e la transazione non partirebbe
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    // ============================================
    // Giro delle scadenze
    // ============================================
    // Legge le candidate senza lock e senza transazione propria, poi elabora ognuna in una
    // transazione separata con il lock in scrittura: un errore su una partita annulla solo la sua
    // transazione, viene loggato e il giro passa alla successiva.
    public void eseguiGiro() {
        LocalDateTime adesso = LocalDateTime.now(clock);

        // Solo poche righe: CREATA e CONFERMATA incomplete con data_partita fino a dopodomani.
        // Il filtro sull'inizio effettivo (partite dopo mezzanotte) si fa qui in Java.
        List<Partita> candidate = partitaRepository.findCandidateScadenze(StatoPartita.CREATA,
                StatoPartita.CONFERMATA, RegoleConfermaPartita.getPostiMax(),
                adesso.toLocalDate().plusDays(GIORNI_CANDIDATE));

        for (Partita candidata : candidate) {
            // Prima di −24 h nessun passo è maturato: non serve aprire una transazione
            if (RegoleTempoPartita.primaDelLimiteModifiche(candidata, adesso)) {
                continue;
            }
            Integer idPartita = candidata.getId();
            try {
                transactionTemplate.executeWithoutResult(stato -> elaboraPartita(idPartita, adesso));
            } catch (RuntimeException ex) {
                log.error("Scadenze: partita {}, errore durante l'elaborazione, si passa alla successiva",
                        idPartita, ex);
            }
        }
    }

    // ============================================
    // Elaborazione di una partita
    // ============================================
    // Gira dentro la transazione aperta da eseguiGiro. Lo stato si rilegge con il lock, perché tra la
    // lettura delle candidate e questo punto un utente può aver confermato, essere uscito o eliminato
    // la partita: tutte le condizioni si ricontrollano qui sul dato aggiornato.
    private void elaboraPartita(Integer idPartita, LocalDateTime adesso) {
        Optional<Partita> letta = partitaRepository.findByIdPerAggiornamento(idPartita);
        if (letta.isEmpty()) {
            log.info("Scadenze: partita {}, esito NESSUNA_AZIONE (non più presente)", idPartita);
            return;
        }
        Partita partita = letta.get();

        if (RegoleTempoPartita.iniziata(partita, adesso)) {
            passoInizio(partita);
        } else {
            passoAvviso(partita, adesso);
        }
        // −12 h: non c'è niente da salvare. La finestra di conferma si chiude per RegoleTempoPartita
        // e la partita resta CREATA se qualche utente non ha confermato (RF_GC_8), fino all'inizio.
    }

    // −24 h (RF_GC_1, RF_GC_2, RF_GA_8): una partita creata con meno di 4 posti resta CREATA, perché
    // l'admin può ancora completarla, e riceve un solo avviso PARTITA_INCOMPLETA (vincolo uk_avviso).
    private void passoAvviso(Partita partita, LocalDateTime adesso) {
        if (partita.getStato() != StatoPartita.CREATA) {
            log.debug("Scadenze: partita {}, passo {}, esito NESSUNA_AZIONE (stato {})",
                    partita.getId(), PASSO_AVVISO, partita.getStato());
            return;
        }

        // T13: notifica RICHIESTA_CONFERMA a ogni giocatore utente (RF_GN_2), una sola volta per partita

        int posti = partita.getPartecipazioni().size();
        if (RegoleConfermaPartita.alCompleto(partita)) {
            log.debug("Scadenze: partita {}, passo {}, esito NESSUNA_AZIONE (posti {})",
                    partita.getId(), PASSO_AVVISO, posti);
            return;
        }
        // Il lock sulla partita serializza i giri: il controllo e l'inserimento non si sovrappongono
        if (avvisoRepository.existsByPartitaIdAndTipo(partita.getId(), TipoAvviso.PARTITA_INCOMPLETA)) {
            log.debug("Scadenze: partita {}, passo {}, esito NESSUNA_AZIONE (avviso già presente)",
                    partita.getId(), PASSO_AVVISO);
            return;
        }

        Avviso avviso = new Avviso();
        avviso.setPartita(partita);
        avviso.setTipo(TipoAvviso.PARTITA_INCOMPLETA);
        // Dal Clock iniettato, come "adesso", e non dal default dell'Entity
        avviso.setDataCreazione(adesso);
        avvisoRepository.save(avviso);

        // T13: notifica ADMIN_INCOMPLETA all'amministratore (RF_GN_9)

        log.info("Scadenze: partita {}, passo {}, esito AVVISO_CREATO (PARTITA_INCOMPLETA, posti {})",
                partita.getId(), PASSO_AVVISO, posti);
    }

    // Inizio (RF_GC_9, RF_GC_10, RNF_A_1): si gioca solo una partita CONFERMATA con 4 posti, ogni altra
    // partita creata o confermata passa ad ANNULLATA. Una già annullata o giocata non si tocca.
    private void passoInizio(Partita partita) {
        StatoPartita stato = partita.getStato();
        int posti = partita.getPartecipazioni().size();
        boolean daAnnullare = stato == StatoPartita.CREATA
                || (stato == StatoPartita.CONFERMATA && !RegoleConfermaPartita.alCompleto(partita));
        if (!daAnnullare) {
            log.debug("Scadenze: partita {}, passo {}, esito NESSUNA_AZIONE (stato {}, posti {})",
                    partita.getId(), PASSO_INIZIO, stato, posti);
            return;
        }

        // La partita è gestita dall'EntityManager: il cambio di stato lo scrive il dirty checking al commit.
        // Con ANNULLATA la colonna generata slot_attivo diventa NULL e lo slot torna prenotabile.
        partita.setStato(StatoPartita.ANNULLATA);

        // T13: notifiche ANNULLAMENTO ai giocatori utenti (RF_GN_3) e ADMIN_ANNULLAMENTO all'amministratore (RF_GN_7)

        log.info("Scadenze: partita {}, passo {}, esito ANNULLATA (era {}, posti {})",
                partita.getId(), PASSO_INIZIO, stato, posti);
    }
}
