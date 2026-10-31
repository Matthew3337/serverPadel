package com.padelbooking.api.service;

import com.padelbooking.api.model.Campo;
import com.padelbooking.api.model.Partita;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

// Regole temporali di una partita condivise dai service (RNF_S_2): inizio e fine effettivi
// e finestre in cui sono ammesse le azioni. Le soglie stanno solo qui, così PrenotazioneService
// e PartitaService non possono divergere. Classe di soli metodi statici: non ha stato,
// "adesso" lo passa il chiamante leggendolo dal proprio Clock.
public final class RegoleTempoPartita {

    // Fino a quante ore prima dell'inizio si può ancora annullare, invitare o uscire
    private static final int LIMITE_MODIFICHE_ORE = 24;

    // Fino a quante ore prima dell'inizio si può confermare la presenza (la finestra apre a LIMITE_MODIFICHE_ORE)
    private static final int LIMITE_CONFERMA_ORE = 12;

    // Per quante ore dopo la fine si può registrare il risultato
    private static final int FINESTRA_RISULTATO_ORE = 24;

    private RegoleTempoPartita() {
    }

    // ============================================
    // Inizio e fine effettivi
    // ============================================

    // data_partita è la data della serata, come la mostra slot-disponibili: in un campo che chiude
    // a mezzanotte o dopo (chiusura <= apertura, compreso il caso 24 ore), uno slot che inizia prima
    // dell'ora di apertura appartiene alla parte dopo mezzanotte, quindi inizia il giorno successivo.
    public static LocalDateTime inizioEffettivo(Campo campo, LocalDate dataSerata, LocalTime oraInizio) {
        boolean chiudeDopoMezzanotte = !campo.getOraChiusura().isAfter(campo.getOraApertura());
        boolean dopoMezzanotte = chiudeDopoMezzanotte && oraInizio.isBefore(campo.getOraApertura());
        LocalDate giorno = dopoMezzanotte ? dataSerata.plusDays(1) : dataSerata;
        return LocalDateTime.of(giorno, oraInizio);
    }

    public static LocalDateTime inizioEffettivo(Partita partita) {
        return inizioEffettivo(partita.getCampo(), partita.getDataPartita(), partita.getOraInizio());
    }

    // La fine si ricava dall'inizio effettivo più la durata: una partita 23:30-01:00 finisce
    // il giorno dopo il suo inizio, e confrontare solo gli orari darebbe una durata negativa
    public static LocalDateTime fineEffettiva(Partita partita) {
        long secondiInizio = partita.getOraInizio().toSecondOfDay();
        long secondiFine = partita.getOraFine().toSecondOfDay();
        long durataSecondi = Math.floorMod(secondiFine - secondiInizio, 24 * 60 * 60L);
        return inizioEffettivo(partita).plusSeconds(durataSecondi);
    }

    // ============================================
    // Finestre temporali delle azioni
    // ============================================

    // adesso < inizio - 24 h: annullamento da parte del creatore, inviti, uscita
    public static boolean primaDelLimiteModifiche(Partita partita, LocalDateTime adesso) {
        return adesso.isBefore(inizioEffettivo(partita).minusHours(LIMITE_MODIFICHE_ORE));
    }

    // inizio - 24 h <= adesso < inizio - 12 h: conferma della presenza
    public static boolean inFinestraConferma(Partita partita, LocalDateTime adesso) {
        LocalDateTime inizio = inizioEffettivo(partita);
        return !adesso.isBefore(inizio.minusHours(LIMITE_MODIFICHE_ORE))
                && adesso.isBefore(inizio.minusHours(LIMITE_CONFERMA_ORE));
    }

    // fine <= adesso <= fine + 24 h: registrazione del risultato
    public static boolean inFinestraRisultato(Partita partita, LocalDateTime adesso) {
        LocalDateTime fine = fineEffettiva(partita);
        return !adesso.isBefore(fine) && !adesso.isAfter(fine.plusHours(FINESTRA_RISULTATO_ORE));
    }

    // Serve ai messaggi d'errore, che riportano la soglia in ore
    public static int getLimiteModificheOre() {
        return LIMITE_MODIFICHE_ORE;
    }
}
