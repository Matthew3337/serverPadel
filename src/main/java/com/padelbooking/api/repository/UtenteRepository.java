package com.padelbooking.api.repository;

import com.padelbooking.api.model.Utente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UtenteRepository extends JpaRepository<Utente, String> {

    Optional<Utente> findByTelefono(String telefono);

    boolean existsByTelefono(String telefono);

    // Utenti invitabili in una partita (RF_GI_2): non admin, di qualunque livello, senza un posto nella
    // partita (quindi escluso anche il creatore), con nome o cognome che contiene il pattern.
    // Il pattern arriva già in minuscolo, con % ai lati e i caratteri speciali di LIKE preceduti da '!':
    // il carattere di escape non è la barra rovesciata perché MySQL la tratta come escape anche
    // dentro le stringhe. Il terzo criterio di ordinamento rende stabile l'ordine tra omonimi.
    @Query("""
            SELECT u FROM Utente u
            WHERE u.isAdmin = false
              AND NOT EXISTS (SELECT pa FROM Partecipazione pa
                              WHERE pa.partita.id = :idPartita
                                AND pa.utente = u)
              AND (LOWER(u.nome) LIKE :pattern ESCAPE '!'
                   OR LOWER(u.cognome) LIKE :pattern ESCAPE '!')
            ORDER BY u.cognome ASC, u.nome ASC, u.telefono ASC
            """)
    List<Utente> findInvitabili(@Param("idPartita") Integer idPartita, @Param("pattern") String pattern);

    // Gestione livelli (UC_GU_3): gli utenti non admin con nome o cognome che contiene il pattern, che
    // arriva già pronto come per findInvitabili. Il telefono rende stabile l'ordine tra omonimi.
    @Query("""
            SELECT u FROM Utente u
            WHERE u.isAdmin = false
              AND (LOWER(u.nome) LIKE :pattern ESCAPE '!'
                   OR LOWER(u.cognome) LIKE :pattern ESCAPE '!')
            ORDER BY u.cognome ASC, u.nome ASC, u.telefono ASC
            """)
    List<Utente> findNonAdmin(@Param("pattern") String pattern);

    // Classifica (RF_GCL_2): gli utenti non admin per punteggio decrescente, a parità per cognome e nome.
    // Il telefono rende stabile l'ordine tra omonimi.
    @Query("""
            SELECT u FROM Utente u
            WHERE u.isAdmin = false
            ORDER BY u.punteggio DESC, u.cognome ASC, u.nome ASC, u.telefono ASC
            """)
    List<Utente> findClassifica();

    // Eliminazione dell'account (T18): gli admin diversi da chi si elimina, per telefono. Il primo riceve le
    // partite non ancora finite create da chi si elimina.
    @Query("""
            SELECT u FROM Utente u
            WHERE u.isAdmin = true
              AND u.telefono <> :escluso
            ORDER BY u.telefono ASC
            """)
    List<Utente> findAdminTranne(@Param("escluso") String escluso);
}
