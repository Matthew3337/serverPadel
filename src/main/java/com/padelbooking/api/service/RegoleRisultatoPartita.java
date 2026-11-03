package com.padelbooking.api.service;

import com.padelbooking.api.model.Partita;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Regole del risultato di una partita condivise dai service: quali set sono validi, quali sono stati
// giocati e quale squadra ha vinto. Serve allo storico (T6), alla registrazione del risultato (T9)
// e al calcolo dei punteggi (T10), così il vincitore si stabilisce in un solo punto.
// Classe di soli metodi statici, come RegoleTempoPartita.
public final class RegoleRisultatoPartita {

    // Le squadre di una partita: partecipazione.squadra vale 1 o 2
    public static final int SQUADRA_1 = 1;
    public static final int SQUADRA_2 = 2;

    // Al meglio dei 3 set: un risultato registrato ha almeno 2 set giocati
    private static final int SET_MIN = 2;
    private static final int SET_MAX = 3;

    // Una squadra è composta da 2 giocatori
    private static final int GIOCATORI_PER_SQUADRA = 2;

    // RF_GR_6: si vince a 6 game con almeno 2 di scarto, altrimenti si arriva a 7 (7-5 o 7-6)
    private static final int GAME_MIN = 0;
    private static final int GAME_VITTORIA = 6;
    private static final int GAME_VITTORIA_AL_LIMITE = 7;
    private static final int SCARTO_MINIMO = 2;

    // Messaggi del rifiuto (RNF_U_3): fanno parte del contratto con il client
    private static final String MESSAGGIO_NUMERO_SET =
            "Il risultato deve avere 2 o 3 set e una squadra deve vincerne esattamente 2";
    private static final String MESSAGGIO_TERZO_SET_DI_TROPPO =
            "Il terzo set non va inserito se una squadra ha vinto i primi due";
    private static final String MOTIVO_SET_NON_VALIDO =
            " non è valido: si vince a 6 game con almeno 2 di scarto, oppure 7-5 o 7-6";

    private RegoleRisultatoPartita() {
    }

    // I game di un set: squadra1 e squadra2 sono quelli delle colonne setN_sq1 e setN_sq2
    public record SetGiocato(int squadra1, int squadra2) {
    }

    // I game di un set come arrivano dalla richiesta, non ancora validati: possono mancare
    public record SetInserito(Integer squadra1, Integer squadra2) {
    }

    // ============================================
    // Validità dei set inseriti
    // ============================================

    // Motivo per cui i set inseriti non formano un risultato valido, vuoto se lo formano (RF_GR_5, RF_GR_6).
    // Prima ogni set da solo, poi il loro numero e chi li ha vinti. Con i set già validi nessuno è in
    // parità, quindi i primi due danno un vincitore solo se li ha vinti entrambi la stessa squadra:
    // in tal caso bastano due set, altrimenti serve il terzo, che porta per forza qualcuno a 2.
    public static Optional<String> rifiutoSet(List<SetInserito> set) {
        if (set == null) {
            return Optional.of(MESSAGGIO_NUMERO_SET);
        }
        for (int i = 0; i < set.size(); i++) {
            if (!setValido(set.get(i))) {
                return Optional.of("Il set " + (i + 1) + MOTIVO_SET_NON_VALIDO);
            }
        }
        if (set.size() < SET_MIN || set.size() > SET_MAX) {
            return Optional.of(MESSAGGIO_NUMERO_SET);
        }

        List<SetGiocato> primiDue = set.subList(0, SET_MIN).stream()
                .map(s -> new SetGiocato(s.squadra1(), s.squadra2()))
                .toList();
        boolean primiDueStessaSquadra = squadraVincitrice(primiDue) != null;
        if (set.size() == SET_MIN && !primiDueStessaSquadra) {
            return Optional.of(MESSAGGIO_NUMERO_SET);
        }
        if (set.size() == SET_MAX && primiDueStessaSquadra) {
            return Optional.of(MESSAGGIO_TERZO_SET_DI_TROPPO);
        }
        return Optional.empty();
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
    // Punti
    // ============================================

    // I punti si calcolano da un risultato con un vincitore e da due squadre complete. Un risultato salvato può
    // non averle più: se l'admin toglie la partita da GIOCATA e cambia i partecipanti, chi è uscito non c'è più
    // e chi è entrato non ha una squadra. Assegnare i punti darebbe -1 a chi non ha giocato.
    public static boolean puntiCalcolabili(Partita partita) {
        return risultatoRegistrato(partita)
                && squadraVincitrice(partita) != null
                && giocatoriInSquadra(partita, SQUADRA_1) == GIOCATORI_PER_SQUADRA
                && giocatoriInSquadra(partita, SQUADRA_2) == GIOCATORI_PER_SQUADRA;
    }

    // ============================================
    // Helper privati
    // ============================================

    private static long giocatoriInSquadra(Partita partita, int squadra) {
        return partita.getPartecipazioni().stream()
                .filter(posto -> posto.getSquadra() != null && posto.getSquadra() == squadra)
                .count();
    }

    // La regola non dipende da quale squadra ha vinto: conta solo il punteggio di chi vince e di chi perde
    private static boolean setValido(SetInserito set) {
        if (set.squadra1() == null || set.squadra2() == null) {
            return false;
        }
        int vincente = Math.max(set.squadra1(), set.squadra2());
        int perdente = Math.min(set.squadra1(), set.squadra2());
        if (perdente < GAME_MIN) {
            return false;
        }
        if (vincente == GAME_VITTORIA) {
            return vincente - perdente >= SCARTO_MINIMO;
        }
        return vincente == GAME_VITTORIA_AL_LIMITE && perdente >= GAME_VITTORIA_AL_LIMITE - SCARTO_MINIMO
                && perdente < GAME_VITTORIA_AL_LIMITE;
    }

    private static void aggiungiSeGiocato(List<SetGiocato> set, Integer gameSquadra1, Integer gameSquadra2) {
        if (gameSquadra1 != null && gameSquadra2 != null) {
            set.add(new SetGiocato(gameSquadra1, gameSquadra2));
        }
    }
}
