package com.padelbooking.api.service;

import com.padelbooking.api.model.Partita;

import java.util.ArrayList;
import java.util.List;

// Regole del risultato di una partita condivise dai service: quali set sono stati giocati
// e quale squadra ha vinto. Serve allo storico (T6) e servirà alla registrazione del risultato (T9)
// e al calcolo dei punteggi (T10), così il vincitore si stabilisce in un solo punto.
// Classe di soli metodi statici, come RegoleTempoPartita.
public final class RegoleRisultatoPartita {

    // Le squadre di una partita: partecipazione.squadra vale 1 o 2
    public static final int SQUADRA_1 = 1;
    public static final int SQUADRA_2 = 2;

    // Al meglio dei 3 set: un risultato registrato ha almeno 2 set giocati
    private static final int SET_MIN = 2;

    private RegoleRisultatoPartita() {
    }

    // I game di un set: squadra1 e squadra2 sono quelli delle colonne setN_sq1 e setN_sq2
    public record SetGiocato(int squadra1, int squadra2) {
    }

    // ============================================
    // Set giocati
    // ============================================

    // Set con entrambe le colonne valorizzate, in ordine: il terzo set è NULL se la partita è finita 2-0.
    // Un set con una sola colonna valorizzata non è un set giocato e si ignora.
    public static List<SetGiocato> setGiocati(Partita partita) {
        List<SetGiocato> set = new ArrayList<>();
        aggiungiSeGiocato(set, partita.getSet1Sq1(), partita.getSet1Sq2());
        aggiungiSeGiocato(set, partita.getSet2Sq1(), partita.getSet2Sq2());
        aggiungiSeGiocato(set, partita.getSet3Sq1(), partita.getSet3Sq2());
        return set;
    }

    // Il risultato è registrato quando ci sono almeno i primi due set. Non si usa data_risultato:
    // conta quello che serve a mostrare il risultato, cioè i game dei set.
    public static boolean risultatoRegistrato(Partita partita) {
        return setGiocati(partita).size() >= SET_MIN;
    }

    // ============================================
    // Vincitore
    // ============================================

    // Vince la squadra che ha vinto più set; un set vinto è uno in cui ha fatto più game dell'altra.
    // Restituisce SQUADRA_1, SQUADRA_2, oppure null se i set vinti sono pari (risultato non valido,
    // per esempio un solo set o un set in parità): chi chiama decide come trattarlo.
    public static Integer squadraVincitrice(List<SetGiocato> set) {
        int vintiSquadra1 = 0;
        int vintiSquadra2 = 0;
        for (SetGiocato s : set) {
            if (s.squadra1() > s.squadra2()) {
                vintiSquadra1++;
            } else if (s.squadra2() > s.squadra1()) {
                vintiSquadra2++;
            }
        }
        if (vintiSquadra1 == vintiSquadra2) {
            return null;
        }
        return vintiSquadra1 > vintiSquadra2 ? SQUADRA_1 : SQUADRA_2;
    }

    public static Integer squadraVincitrice(Partita partita) {
        return squadraVincitrice(setGiocati(partita));
    }

    // ============================================
    // Helper privati
    // ============================================

    private static void aggiungiSeGiocato(List<SetGiocato> set, Integer gameSquadra1, Integer gameSquadra2) {
        if (gameSquadra1 != null && gameSquadra2 != null) {
            set.add(new SetGiocato(gameSquadra1, gameSquadra2));
        }
    }
}
