package com.padelbooking.api.repository;

import com.padelbooking.api.model.Prenotazione;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface PrenotazioneRepository extends JpaRepository<Prenotazione, Integer> {

    // Tutte le prenotazioni di un campo in una data (per calcolare gli slot liberi).
    // Non esiste più una colonna "stato": una riga presente in tabella è per definizione attiva,
    // perché la cancellazione ora esegue una DELETE reale della riga.
    List<Prenotazione> findByCampoIdAndDataPrenotazione(Integer campoId, LocalDate dataPrenotazione);

    // Verifica se uno slot specifico è già occupato
    boolean existsByCampoIdAndDataPrenotazioneAndOraInizio(
            Integer campoId, LocalDate dataPrenotazione, LocalTime oraInizio);

    // Tutte le prenotazioni in cui l'utente compare, in uno qualsiasi dei 4 slot giocatore
    @Query("""
            SELECT p FROM Prenotazione p
            WHERE p.giocatore1.id = :utenteId
               OR p.giocatore2.id = :utenteId
               OR p.giocatore3.id = :utenteId
               OR p.giocatore4.id = :utenteId
            ORDER BY p.dataPrenotazione DESC, p.oraInizio DESC
            """)
    List<Prenotazione> findByGiocatoreId(@Param("utenteId") Integer utenteId);

    // Prima prenotazione futura (rispetto a oggi/ora) di un utente identificato dal telefono,
    // considerando l'utente in uno qualsiasi dei 4 slot giocatore.
    @Query("""
            SELECT p FROM Prenotazione p
            WHERE (p.giocatore1.telefono = :telefono
               OR p.giocatore2.telefono = :telefono
               OR p.giocatore3.telefono = :telefono
               OR p.giocatore4.telefono = :telefono)
              AND (p.dataPrenotazione > :oggi
                   OR (p.dataPrenotazione = :oggi AND p.oraInizio >= :oraAttuale))
            ORDER BY p.dataPrenotazione ASC, p.oraInizio ASC
            """)
    List<Prenotazione> findProssimeByTelefono(@Param("telefono") String telefono,
                                               @Param("oggi") LocalDate oggi,
                                               @Param("oraAttuale") LocalTime oraAttuale);
}
