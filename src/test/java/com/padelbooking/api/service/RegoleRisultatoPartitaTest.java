package com.padelbooking.api.service;

import com.padelbooking.api.model.Partecipazione;
import com.padelbooking.api.model.Partita;
import com.padelbooking.api.model.TipoPartecipazione;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
        assertThat(RegoleRisultatoPartita.rifiutoSet(set(0, 6, 4, 6))).isEmpty();
    }

    @Test
    void treSetConPrimiDueDivisiSonoValidi() {
        assertThat(RegoleRisultatoPartita.rifiutoSet(set(6, 4, 3, 6, 6, 2))).isEmpty();
        assertThat(RegoleRisultatoPartita.rifiutoSet(set(3, 6, 6, 4, 0, 6))).isEmpty();
    }

    // Tutti i punteggi validi, nei due sensi: 6 a un massimo di 4, 7-5, 7-6
    private static final Set<String> PUNTEGGI_VALIDI = Set.of(
            "6-0", "6-1", "6-2", "6-3", "6-4", "7-5", "7-6",
            "0-6", "1-6", "2-6", "3-6", "4-6", "5-7", "6-7");

    @Test
    void ogniPunteggioEValidoSoloSeEInElenco() {
        // Ogni coppia di game da -1 a 9, come primo set: il secondo vale per la stessa squadra che vince il primo,
        // così il risultato completo è sempre valido e l'unico motivo di rifiuto può essere il set in prova
        for (int squadra1 = -1; squadra1 <= 9; squadra1++) {
            for (int squadra2 = -1; squadra2 <= 9; squadra2++) {
                String punteggio = squadra1 + "-" + squadra2;
                Integer[] secondoSet = squadra1 > squadra2 ? new Integer[] {6, 0} : new Integer[] {0, 6};
                Optional<String> rifiuto = RegoleRisultatoPartita.rifiutoSet(
                        set(squadra1, squadra2, secondoSet[0], secondoSet[1]));

                if (PUNTEGGI_VALIDI.contains(punteggio)) {
                    assertThat(rifiuto).as(punteggio).isEmpty();
                } else {
                    assertThat(rifiuto).as(punteggio).contains(setNonValido(1));
                }
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"5,4", "6,5", "7,4", "6,6", "7,7", "0,0", "8,6", "4,5", "5,6", "4,7", "6,8", "7,0", "5,5"})
    void punteggiDelContrattoRifiutati(int squadra1, int squadra2) {
        assertThat(RegoleRisultatoPartita.rifiutoSet(set(squadra1, squadra2, 6, 3))).contains(setNonValido(1));
    }

    @ParameterizedTest
    @CsvSource({"7,5", "7,6", "5,7", "6,7"})
    void setAl7ValidiInOgniPosizione(int squadra1, int squadra2) {
        int vinceSquadra1 = squadra1 > squadra2 ? 6 : 0;
        int vinceSquadra2 = squadra1 > squadra2 ? 0 : 6;

        assertThat(RegoleRisultatoPartita.rifiutoSet(
                set(squadra1, squadra2, vinceSquadra1, vinceSquadra2))).isEmpty();
        assertThat(RegoleRisultatoPartita.rifiutoSet(
                set(vinceSquadra1, vinceSquadra2, squadra1, squadra2))).isEmpty();
        // Terzo set decisivo: i primi due sono divisi
        assertThat(RegoleRisultatoPartita.rifiutoSet(set(6, 4, 3, 6, squadra1, squadra2))).isEmpty();
    }

    @Test
    void ilMessaggioIndicaIlSetSbagliato() {
        assertThat(RegoleRisultatoPartita.rifiutoSet(set(6, 5, 6, 3))).contains(setNonValido(1));
        assertThat(RegoleRisultatoPartita.rifiutoSet(set(6, 4, 3, 5))).contains(setNonValido(2));
        assertThat(RegoleRisultatoPartita.rifiutoSet(set(6, 4, 3, 6, 8, 6))).contains(setNonValido(3));
        assertThat(setNonValido(2))
                .isEqualTo("Il set 2 non è valido: si vince a 6 game con almeno 2 di scarto, oppure 7-5 o 7-6");
    }

    @Test
    void gameNegativoNonEValido() {
        assertThat(RegoleRisultatoPartita.rifiutoSet(set(-1, 6, 6, 3))).contains(setNonValido(1));
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
        return "Il set " + numero + " non è valido: si vince a 6 game con almeno 2 di scarto, oppure 7-5 o 7-6";
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
