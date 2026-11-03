package com.padelbooking.api.service;

import com.padelbooking.api.model.Partecipazione;
import com.padelbooking.api.model.Partita;
import com.padelbooking.api.model.TipoPartecipazione;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegoleRisultatoPartitaTest {

    // ============================================
    // Set giocati
    // ============================================

    @Test
    void senzaSetNessunRisultato() {
        Partita p = new Partita();

        assertThat(RegoleRisultatoPartita.setGiocati(p)).isEmpty();
        assertThat(RegoleRisultatoPartita.risultatoRegistrato(p)).isFalse();
        assertThat(RegoleRisultatoPartita.squadraVincitrice(p)).isNull();
    }

    @Test
    void dueSetIgnorandoIlTerzoNull() {
        Partita p = partita(6, 4, 6, 3, null, null);

        assertThat(RegoleRisultatoPartita.setGiocati(p)).containsExactly(
                new RegoleRisultatoPartita.SetGiocato(6, 4), new RegoleRisultatoPartita.SetGiocato(6, 3));
        assertThat(RegoleRisultatoPartita.risultatoRegistrato(p)).isTrue();
    }

    @Test
    void setConUnaSolaColonnaNonEGiocato() {
        Partita p = partita(6, 4, 6, null, null, null);

        assertThat(RegoleRisultatoPartita.setGiocati(p)).hasSize(1);
        assertThat(RegoleRisultatoPartita.risultatoRegistrato(p)).isFalse();
    }

    // ============================================
    // Vincitore
    // ============================================

    @Test
    void dueSetVintiDallaSquadraUno() {
        assertThat(RegoleRisultatoPartita.squadraVincitrice(partita(6, 4, 6, 3, null, null)))
                .isEqualTo(RegoleRisultatoPartita.SQUADRA_1);
    }

    @Test
    void dueSetVintiDallaSquadraDue() {
        assertThat(RegoleRisultatoPartita.squadraVincitrice(partita(4, 6, 0, 6, null, null)))
                .isEqualTo(RegoleRisultatoPartita.SQUADRA_2);
    }

    @Test
    void treSetVintiDallaSquadraUno() {
        assertThat(RegoleRisultatoPartita.squadraVincitrice(partita(6, 4, 3, 6, 6, 2)))
                .isEqualTo(RegoleRisultatoPartita.SQUADRA_1);
    }

    @Test
    void treSetVintiDallaSquadraDueContandoISetNonIGame() {
        // La squadra 1 fa più game in totale (15 contro 13), ma vince chi ha vinto più set
        assertThat(RegoleRisultatoPartita.squadraVincitrice(partita(6, 0, 5, 7, 4, 6)))
                .isEqualTo(RegoleRisultatoPartita.SQUADRA_2);
    }

    @Test
    void setVintiPariNessunVincitore() {
        assertThat(RegoleRisultatoPartita.squadraVincitrice(List.of(
                new RegoleRisultatoPartita.SetGiocato(6, 4), new RegoleRisultatoPartita.SetGiocato(4, 6))))
                .isNull();
    }

    @Test
    void setInParitaDiGameNonEVintoDaNessuno() {
        // 6-6 non assegna il set: decide l'altro set
        assertThat(RegoleRisultatoPartita.squadraVincitrice(partita(6, 6, 4, 6, null, null)))
                .isEqualTo(RegoleRisultatoPartita.SQUADRA_2);
    }

    @Test
    void listaVuotaNessunVincitore() {
        assertThat(RegoleRisultatoPartita.squadraVincitrice(List.of())).isNull();
    }

    @Test
    void treSetConservanoLOrdine() {
        assertThat(RegoleRisultatoPartita.setGiocati(partita(6, 4, 3, 6, 6, 2))).containsExactly(
                new RegoleRisultatoPartita.SetGiocato(6, 4), new RegoleRisultatoPartita.SetGiocato(3, 6),
                new RegoleRisultatoPartita.SetGiocato(6, 2));
    }

    // ============================================
    // Validità dei set inseriti
    // ============================================

    private static final String NUMERO_SET = "Il risultato deve avere 2 o 3 set e una squadra deve vincerne esattamente 2";
    private static final String TERZO_DI_TROPPO = "Il terzo set non va inserito se una squadra ha vinto i primi due";

    @Test
    void dueSetVintiDallaStessaSquadraSonoValidi() {
        assertThat(RegoleRisultatoPartita.rifiutoSet(set(6, 4, 6, 3))).isEmpty();
        assertThat(RegoleRisultatoPartita.rifiutoSet(set(0, 6, 5, 6))).isEmpty();
    }

    @Test
    void treSetConPrimiDueDivisiSonoValidi() {
        assertThat(RegoleRisultatoPartita.rifiutoSet(set(6, 4, 3, 6, 6, 2))).isEmpty();
        assertThat(RegoleRisultatoPartita.rifiutoSet(set(3, 6, 6, 4, 0, 6))).isEmpty();
    }

    @Test
    void limitiDeiGameSonoCompresi() {
        assertThat(RegoleRisultatoPartita.rifiutoSet(set(6, 0, 0, 6, 6, 5))).isEmpty();
    }

    @Test
    void gameSopraIlMassimoIndicaIlSetSbagliato() {
        assertThat(RegoleRisultatoPartita.rifiutoSet(set(7, 5, 6, 3))).contains(setNonValido(1));
        assertThat(RegoleRisultatoPartita.rifiutoSet(set(6, 4, 3, 7))).contains(setNonValido(2));
        assertThat(RegoleRisultatoPartita.rifiutoSet(set(6, 4, 3, 6, 7, 6))).contains(setNonValido(3));
    }

    @Test
    void gameNegativoNonEValido() {
        assertThat(RegoleRisultatoPartita.rifiutoSet(set(-1, 6, 6, 3))).contains(setNonValido(1));
    }

    @Test
    void setInParitaNonEValido() {
        assertThat(RegoleRisultatoPartita.rifiutoSet(set(6, 4, 3, 3))).contains(setNonValido(2));
    }

    @Test
    void gameMancanteNonEValido() {
        assertThat(RegoleRisultatoPartita.rifiutoSet(set(6, null, 6, 3))).contains(setNonValido(1));
        assertThat(RegoleRisultatoPartita.rifiutoSet(set(6, 4, null, null))).contains(setNonValido(2));
    }

    @Test
    void listaMancanteOVuotaOUnSoloSet() {
        assertThat(RegoleRisultatoPartita.rifiutoSet(null)).contains(NUMERO_SET);
        assertThat(RegoleRisultatoPartita.rifiutoSet(List.of())).contains(NUMERO_SET);
        assertThat(RegoleRisultatoPartita.rifiutoSet(set(6, 4))).contains(NUMERO_SET);
    }

    @Test
    void quattroSetSonoTroppi() {
        assertThat(RegoleRisultatoPartita.rifiutoSet(set(6, 4, 3, 6, 6, 2, 6, 1))).contains(NUMERO_SET);
    }

    @Test
    void dueSetDivisiNonIndicanoUnVincitore() {
        assertThat(RegoleRisultatoPartita.rifiutoSet(set(6, 4, 4, 6))).contains(NUMERO_SET);
    }

    @Test
    void terzoSetDiTroppoSeUnaSquadraHaVintoIPrimiDue() {
        assertThat(RegoleRisultatoPartita.rifiutoSet(set(6, 4, 6, 3, 2, 6))).contains(TERZO_DI_TROPPO);
        assertThat(RegoleRisultatoPartita.rifiutoSet(set(1, 6, 2, 6, 6, 4))).contains(TERZO_DI_TROPPO);
    }

    @Test
    void ilSetNonValidoHaLaPrecedenzaSulNumeroDeiSet() {
        assertThat(RegoleRisultatoPartita.rifiutoSet(set(6, 6))).contains(setNonValido(1));
    }

    // ============================================
    // Punti
    // ============================================

    @Test
    void puntiCalcolabiliConRisultatoEDueSquadreComplete() {
        assertThat(RegoleRisultatoPartita.puntiCalcolabili(conSquadre(partita(6, 4, 6, 3, null, null), 1, 1, 2, 2)))
                .isTrue();
    }

    @Test
    void puntiNonCalcolabiliSenzaRisultato() {
        assertThat(RegoleRisultatoPartita.puntiCalcolabili(conSquadre(new Partita(), 1, 1, 2, 2))).isFalse();
    }

    @Test
    void puntiNonCalcolabiliSenzaVincitore() {
        assertThat(RegoleRisultatoPartita.puntiCalcolabili(conSquadre(partita(6, 4, 3, 6, null, null), 1, 1, 2, 2)))
                .isFalse();
    }

    @Test
    void puntiNonCalcolabiliSeUnaSquadraNonEPiuCompleta() {
        Partita risultato = partita(6, 4, 6, 3, null, null);

        // Un giocatore è uscito, oppure è stato sostituito da chi non ha una squadra
        assertThat(RegoleRisultatoPartita.puntiCalcolabili(conSquadre(risultato, 1, 1, 2))).isFalse();
        assertThat(RegoleRisultatoPartita.puntiCalcolabili(conSquadre(partita(6, 4, 6, 3, null, null), 1, 1, 2, null)))
                .isFalse();
    }

    private static Partita conSquadre(Partita p, Integer... squadre) {
        for (Integer squadra : squadre) {
            Partecipazione posto = new Partecipazione(TipoPartecipazione.CAMPO, null, false, false);
            posto.setSquadra(squadra);
            p.aggiungiPartecipazione(posto);
        }
        return p;
    }

    private static String setNonValido(int numero) {
        return "Il set " + numero + " non è valido: i game vanno da 0 a 6 e il set non può finire in parità";
    }

    // Coppie di game in ordine: set(6, 4, 3, 6) sono i set 6-4 e 3-6
    private static List<RegoleRisultatoPartita.SetInserito> set(Integer... game) {
        List<RegoleRisultatoPartita.SetInserito> set = new ArrayList<>();
        for (int i = 0; i < game.length; i += 2) {
            set.add(new RegoleRisultatoPartita.SetInserito(game[i], game[i + 1]));
        }
        return set;
    }

    private static Partita partita(Integer s1a, Integer s1b, Integer s2a, Integer s2b, Integer s3a, Integer s3b) {
        Partita p = new Partita();
        p.setSet1Sq1(s1a);
        p.setSet1Sq2(s1b);
        p.setSet2Sq1(s2a);
        p.setSet2Sq2(s2b);
        p.setSet3Sq1(s3a);
        p.setSet3Sq2(s3b);
        return p;
    }
}
