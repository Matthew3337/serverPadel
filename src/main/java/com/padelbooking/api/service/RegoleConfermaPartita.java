package com.padelbooking.api.service;

import com.padelbooking.api.model.Partita;
import com.padelbooking.api.model.StatoPartita;
import com.padelbooking.api.model.TipoPartecipazione;

// Regole sui posti e sul passaggio a "confermata" condivise dai service (RF_GC_5, RF_GC_6, RF_GC_10).
// Stanno in un solo punto perché le usano la conferma dell'utente, l'attività pianificata delle
// scadenze e, in T12, l'aggiunta dei giocatori campo da parte dell'admin (RF_GC_7).
// Classe di soli metodi statici, come RegoleTempoPartita.
public final class RegoleConfermaPartita {

    // Una partita ha al massimo 4 posti (UTENTE o CAMPO) e si gioca solo se li ha tutti occupati
    private static final int POSTI_MAX = 4;

    private RegoleConfermaPartita() {
    }

    // ============================================
    // Posti
    // ============================================

    public static boolean alCompleto(Partita partita) {
        return partita.getPartecipazioni().size() >= POSTI_MAX;
    }

    public static int getPostiMax() {
        return POSTI_MAX;
    }

    // ============================================
    // Passaggio a "confermata"
    // ============================================

    // RF_GC_5: i giocatori campo non hanno account e non confermano, quindi contano solo i posti UTENTE
    public static boolean utentiTuttiConfermati(Partita partita) {
        return partita.getPartecipazioni().stream()
                .filter(pa -> pa.getTipo() == TipoPartecipazione.UTENTE)
                .allMatch(pa -> Boolean.TRUE.equals(pa.getConfermata()));
    }

    // RF_GC_6, RF_GC_7: una partita creata con 4 posti occupati e tutti gli utenti confermati passa
    // a CONFERMATA. Non guarda l'orario: la finestra di conferma la controlla chi registra la conferma,
    // e in T12 l'admin può completare la partita con giocatori campo anche dopo le −12 h.
    // Restituisce true se lo stato è cambiato, così il chiamante sa quando inviare la notifica.
    // La partita deve essere letta con il lock in scrittura, perché il controllo e il cambio di stato
    // non si sovrappongano a un'uscita o a una conferma concorrente.
    public static boolean confermaSeCompleta(Partita partita) {
        if (partita.getStato() != StatoPartita.CREATA || !alCompleto(partita) || !utentiTuttiConfermati(partita)) {
            return false;
        }
        partita.setStato(StatoPartita.CONFERMATA);
        return true;
    }
}
