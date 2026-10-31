package com.padelbooking.api.service;

import com.padelbooking.api.model.Partita;
import org.junit.jupiter.api.Test;

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
