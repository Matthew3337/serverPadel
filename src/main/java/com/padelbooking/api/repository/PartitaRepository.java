package com.padelbooking.api.repository;

import com.padelbooking.api.model.Partita;
import com.padelbooking.api.model.StatoPartita;
import com.padelbooking.api.model.TipoPartecipazione;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface PartitaRepository extends JpaRepository<Partita, Integer> {

    // Partite di un campo in una data con stato diverso da quello indicato
    // (si passa ANNULLATA: le annullate restano in tabella ma non occupano lo slot)
    List<Partita> findByCampoIdAndDataPartitaAndStatoNot(Integer campoId, LocalDate dataPartita, StatoPartita stato);

    // Partite in cui l'utente occupa un posto come UTENTE, escluse quelle con lo stato indicato.
    // EXISTS invece di JOIN per non duplicare la partita se l'utente avesse più righe.
    @Query("""
            SELECT p FROM Partita p
            WHERE p.stato <> :statoEscluso
              AND EXISTS (SELECT pa FROM Partecipazione pa
                          WHERE pa.partita = p
                            AND pa.tipo = :tipoUtente
                            AND pa.utente.telefono = :telefono)
            ORDER BY p.dataPartita DESC, p.oraInizio DESC
            """)
    List<Partita> findByPartecipanteTelefono(@Param("telefono") String telefono,
                                              @Param("tipoUtente") TipoPartecipazione tipoUtente,
                                              @Param("statoEscluso") StatoPartita statoEscluso);

    // Partite future (rispetto a oggi/ora) in cui l'utente occupa un posto come UTENTE,
    // escluse quelle con lo stato indicato, dalla più vicina.
    @Query("""
            SELECT p FROM Partita p
            WHERE p.stato <> :statoEscluso
              AND EXISTS (SELECT pa FROM Partecipazione pa
                          WHERE pa.partita = p
                            AND pa.tipo = :tipoUtente
                            AND pa.utente.telefono = :telefono)
              AND (p.dataPartita > :oggi
                   OR (p.dataPartita = :oggi AND p.oraInizio >= :oraAttuale))
            ORDER BY p.dataPartita ASC, p.oraInizio ASC
            """)
    List<Partita> findProssimeByPartecipanteTelefono(@Param("telefono") String telefono,
                                                      @Param("tipoUtente") TipoPartecipazione tipoUtente,
                                                      @Param("statoEscluso") StatoPartita statoEscluso,
                                                      @Param("oggi") LocalDate oggi,
                                                      @Param("oraAttuale") LocalTime oraAttuale);
}
