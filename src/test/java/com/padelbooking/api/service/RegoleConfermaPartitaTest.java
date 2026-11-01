package com.padelbooking.api.service;

import com.padelbooking.api.model.Partecipazione;
import com.padelbooking.api.model.Partita;
import com.padelbooking.api.model.StatoPartita;
import com.padelbooking.api.model.TipoPartecipazione;
import com.padelbooking.api.model.Utente;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RegoleConfermaPartitaTest {

    @Test
    void quattroUtentiTuttiConfermatiDiventaConfermata() {
        Partita p = partita(StatoPartita.CREATA, true, true, true, true);

        assertThat(RegoleConfermaPartita.confermaSeCompleta(p)).isTrue();
        assertThat(p.getStato()).isEqualTo(StatoPartita.CONFERMATA);
    }

    @Test
    void unUtenteNonConfermatoLasciaCreata() {
        Partita p = partita(StatoPartita.CREATA, true, true, true, false);

        assertThat(RegoleConfermaPartita.confermaSeCompleta(p)).isFalse();
        assertThat(p.getStato()).isEqualTo(StatoPartita.CREATA);
    }

    @Test
    void conMenoDi4PostiLasciaCreata() {
        Partita p = partita(StatoPartita.CREATA, true, true, true);

        assertThat(RegoleConfermaPartita.confermaSeCompleta(p)).isFalse();
        assertThat(p.getStato()).isEqualTo(StatoPartita.CREATA);
    }

    @Test
    void postiCampoNonConfermatiNonContano() {
        // RF_GC_7: l'admin completa con giocatori campo una partita in cui gli utenti hanno confermato
        Partita p = partita(StatoPartita.CREATA, true, true);
        p.aggiungiPartecipazione(new Partecipazione(TipoPartecipazione.CAMPO, null, false, false));
        p.aggiungiPartecipazione(new Partecipazione(TipoPartecipazione.CAMPO, null, false, false));

        assertThat(RegoleConfermaPartita.confermaSeCompleta(p)).isTrue();
        assertThat(p.getStato()).isEqualTo(StatoPartita.CONFERMATA);
    }

    @Test
    void soloLeCreateCambianoStato() {
        for (StatoPartita stato : new StatoPartita[] {StatoPartita.CONFERMATA, StatoPartita.ANNULLATA,
                StatoPartita.GIOCATA}) {
            Partita p = partita(stato, true, true, true, true);

            assertThat(RegoleConfermaPartita.confermaSeCompleta(p)).as("%s", stato).isFalse();
            assertThat(p.getStato()).isEqualTo(stato);
        }
    }

    // Una partecipazione UTENTE per ogni valore di conferma
    private Partita partita(StatoPartita stato, boolean... conferme) {
        Partita p = new Partita();
        p.setStato(stato);
        for (int i = 0; i < conferme.length; i++) {
            Utente u = new Utente("333000000" + i, "Nome", "Cognome", "x", LocalDate.of(1990, 1, 1), false, 2.5);
            p.aggiungiPartecipazione(new Partecipazione(TipoPartecipazione.UTENTE, u, false, conferme[i]));
        }
        return p;
    }
}
