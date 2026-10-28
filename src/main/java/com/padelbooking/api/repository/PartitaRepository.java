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

    // Partite in cui l'utente occupa un posto come UTENTE, escluse quelle con lo stato indicato,
    // a partire da una data e un'ora confrontate con data_partita e ora_inizio.
    // data_partita è la data della serata, quindi questo filtro non coincide con l'istante reale
    // per le partite dopo mezzanotte: il service lo usa solo per restringere le candidate
    // e poi filtra e ordina sull'inizio effettivo.
    @Query("""
            SELECT p FROM Partita p
            WHERE p.stato <> :statoEscluso
              AND EXISTS (SELECT pa FROM Partecipazione pa
                          WHERE pa.partita = p
                            AND pa.tipo = :tipoUtente
                            AND pa.utente.telefono = :telefono)
              AND (p.dataPartita > :dataDa
                   OR (p.dataPartita = :dataDa AND p.oraInizio >= :oraDa))
            ORDER BY p.dataPartita ASC, p.oraInizio ASC
            """)
    List<Partita> findProssimeByPartecipanteTelefono(@Param("telefono") String telefono,
                                                      @Param("tipoUtente") TipoPartecipazione tipoUtente,
                                                      @Param("statoEscluso") StatoPartita statoEscluso,
                                                      @Param("dataDa") LocalDate dataDa,
                                                      @Param("oraDa") LocalTime oraDa);
}
