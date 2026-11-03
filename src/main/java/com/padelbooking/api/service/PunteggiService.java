package com.padelbooking.api.service;

import com.padelbooking.api.model.Impostazioni;
import com.padelbooking.api.model.Partecipazione;
import com.padelbooking.api.model.Partita;
import com.padelbooking.api.model.TipoPartecipazione;
import com.padelbooking.api.model.Utente;
import com.padelbooking.api.repository.ImpostazioniRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

// Variazioni di punteggio di una partita: assegnazione e storno (UC_GCL_2, RF_GCL_3–14).
// I due metodi non aprono una transazione propria (MANDATORY): lavorano in quella di chi li chiama, che
// ha già scritto il risultato, così un errore annulla tutto insieme (RNF_A_7). Chi li chiama deve anche
// avere la partita in lock, perché la stessa partita non venga elaborata due volte in parallelo.
@Service
public class PunteggiService {

    // RF_GCL_3–5: la variazione di un giocatore secondo l'esito e il livello medio delle squadre
    private static final int PUNTI_VITTORIA_CONTRO_LIVELLO_MAGGIORE_O_UGUALE = 3;
    private static final int PUNTI_VITTORIA_CONTRO_LIVELLO_MINORE = 1;
    private static final int PUNTI_SCONFITTA = -1;

    // RF_GCL_9
    private static final int PUNTEGGIO_MIN = 0;

    // La tabella impostazioni ha una sola riga, con questo id (vincolo ck_impostazioni_singola)
    private static final int ID_IMPOSTAZIONI = 1;

    private final ImpostazioniRepository impostazioniRepository;
    private final EntityManager entityManager;

    public PunteggiService(ImpostazioniRepository impostazioniRepository, EntityManager entityManager) {
        this.impostazioniRepository = impostazioniRepository;
        this.entityManager = entityManager;
    }

    // ============================================
    // Assegnazione dei punti
    // ============================================
    // Da chiamare con set e squadre già scritti sulla partita. In punti_assegnati va la variazione
    // effettivamente applicata (RF_GCL_11): un perdente a 0 punti ha variazione 0, non -1.
    // I posti CAMPO contano nella media ma non ricevono punti (RF_GCL_8, RF_GCL_10): per loro
    // punti_assegnati resta null, come impone il vincolo ck_partecipazione_tipo.
    @Transactional(propagation = Propagation.MANDATORY)
    public void assegnaPunti(Partita partita) {
        Integer vincitrice = RegoleRisultatoPartita.squadraVincitrice(partita);
        if (vincitrice == null) {
            throw new IllegalStateException("Partita " + partita.getId() + " senza una squadra vincitrice");
        }

        List<Partecipazione> postiUtente = postiUtente(partita);
        // Prima i lock: i livelli e i punteggi usati sotto sono quelli letti dopo averli ottenuti
        bloccaUtenti(postiUtente);

        double livelloCampo = livelloGiocatoriCampo();
        int perdente = vincitrice == RegoleRisultatoPartita.SQUADRA_1
                ? RegoleRisultatoPartita.SQUADRA_2
                : RegoleRisultatoPartita.SQUADRA_1;
        int puntiVittoria = mediaSquadra(partita, perdente, livelloCampo) >= mediaSquadra(partita, vincitrice, livelloCampo)
                ? PUNTI_VITTORIA_CONTRO_LIVELLO_MAGGIORE_O_UGUALE
                : PUNTI_VITTORIA_CONTRO_LIVELLO_MINORE;

        for (Partecipazione posto : postiUtente) {
            int variazione = vincitrice.equals(posto.getSquadra()) ? puntiVittoria : PUNTI_SCONFITTA;
            Utente utente = posto.getUtente();
            int prima = utente.getPunteggio();
            int dopo = Math.max(PUNTEGGIO_MIN, prima + variazione);
            utente.setPunteggio(dopo);
            posto.setPuntiAssegnati(dopo - prima);
        }
    }

    // ============================================
    // Storno dei punti
    // ============================================
    // RF_GCL_12–14, per la correzione del risultato e per ogni uscita di una partita da GIOCATA:
    // toglie a ogni utente la variazione applicata e azzera punti_assegnati. Una perdita di 1 punto viene
    // quindi restituita. Il punteggio può essere cambiato dopo la partita, per questo vale ancora il
    // minimo 0 (RF_GCL_9). Chiamarlo di nuovo non cambia nulla, perché i posti senza punti si saltano.
    @Transactional(propagation = Propagation.MANDATORY)
    public void stornaPunti(Partita partita) {
        List<Partecipazione> postiConPunti = postiUtente(partita).stream()
                .filter(posto -> posto.getPuntiAssegnati() != null)
                .toList();
        bloccaUtenti(postiConPunti);

        for (Partecipazione posto : postiConPunti) {
            Utente utente = posto.getUtente();
            utente.setPunteggio(Math.max(PUNTEGGIO_MIN, utente.getPunteggio() - posto.getPuntiAssegnati()));
            posto.setPuntiAssegnati(null);
        }
        // Il refresh di bloccaUtenti sostituisce i valori in memoria con quelli del database: se assegnaPunti
        // segue nella stessa transazione, come nella correzione, senza il flush lo storno andrebbe perso.
        // Chi chiama deve stornare prima di scrivere il risultato (vedi il commento su bloccaUtenti).
        entityManager.flush();
    }

    // ============================================
    // Helper privati
    // ============================================

    private List<Partecipazione> postiUtente(Partita partita) {
        return partita.getPartecipazioni().stream()
                .filter(posto -> posto.getTipo() == TipoPartecipazione.UTENTE)
                .toList();
    }

    // Lock in scrittura sulle righe utente, una alla volta e sempre in ordine di telefono: due partite con
    // giocatori in comune registrate insieme si mettono in coda invece di andare in deadlock.
    // Si usa refresh e non una query con lock: se l'utente è già nella sessione, una query lo blocca ma
    // lascia i valori letti prima, e il punteggio scritto da chi ha appena rilasciato il lock andrebbe perso.
    // Niente flush qui: l'UPDATE di partita con telefono_registrante prende un lock condiviso sulla riga
    // utente per la chiave esterna, e due registrazioni che lo tengono e chiedono quello in scrittura
    // sulla stessa riga vanno in deadlock. Va scritto solo dopo aver preso questi lock.
    private void bloccaUtenti(List<Partecipazione> posti) {
        posti.stream()
                .map(Partecipazione::getUtente)
                .sorted(Comparator.comparing(Utente::getTelefono))
                .forEach(utente -> entityManager.refresh(utente, LockModeType.PESSIMISTIC_WRITE));
    }

    // Senza la riga non si può calcolare la media: meglio far fallire la registrazione che inventare un livello
    private double livelloGiocatoriCampo() {
        return impostazioniRepository.findById(ID_IMPOSTAZIONI)
                .map(Impostazioni::getLivelloGiocatoriCampo)
                .orElseThrow(() -> new IllegalStateException("Riga " + ID_IMPOSTAZIONI + " di impostazioni mancante"));
    }

    // RF_GCL_7–8: con il livello attuale dei giocatori. I livelli sono multipli di 0,5 e le squadre di
    // 2 giocatori: le medie sono numeri esatti in double, quindi il confronto con >= è affidabile.
    private double mediaSquadra(Partita partita, int squadra, double livelloCampo) {
        return partita.getPartecipazioni().stream()
                .filter(posto -> posto.getSquadra() != null && posto.getSquadra() == squadra)
                .mapToDouble(posto -> posto.getTipo() == TipoPartecipazione.UTENTE
                        ? posto.getUtente().getLivello()
                        : livelloCampo)
                .average()
                .orElseThrow(() -> new IllegalStateException(
                        "Partita " + partita.getId() + " senza giocatori nella squadra " + squadra));
    }
}
