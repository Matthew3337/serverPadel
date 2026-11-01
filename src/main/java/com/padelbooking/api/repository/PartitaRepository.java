package com.padelbooking.api.repository;

import com.padelbooking.api.model.Partita;
import com.padelbooking.api.model.StatoPartita;
import com.padelbooking.api.model.TipoPartecipazione;
import com.padelbooking.api.model.TipoPartita;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

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

    // Candidate per la bacheca (RF_GP_11): partite di un tipo e di uno stato con data_partita da una data in poi.
    // Campo e creatore in JOIN FETCH perché servono a ogni riga della risposta.
    // Come sopra, data_partita è la data della serata: il service filtra e ordina sull'inizio effettivo.
    @Query("""
            SELECT p FROM Partita p
            JOIN FETCH p.campo
            JOIN FETCH p.creatore
            WHERE p.tipo = :tipo
              AND p.stato = :stato
              AND p.dataPartita >= :dataDa
            """)
    List<Partita> findByTipoAndStatoDaData(@Param("tipo") TipoPartita tipo,
                                           @Param("stato") StatoPartita stato,
                                           @Param("dataDa") LocalDate dataDa);

    // Candidate per l'attività pianificata delle scadenze (ScadenzePartiteService): le partite CREATA, e le
    // CONFERMATA con meno di 4 posti, con data_partita fino a una data, senza limite verso il passato,
    // così dopo un fermo si recuperano anche le scadenze già maturate (RNF_A_3).
    // Le CONFERMATA con 4 posti si escludono già qui: per loro non c'è nessun passo da fare, e
    // altrimenti quelle passate senza risultato verrebbero rilette a ogni giro.
    // Campo in JOIN FETCH perché serve all'inizio effettivo, calcolato in Java come nella bacheca.
    @Query("""
            SELECT p FROM Partita p
            JOIN FETCH p.campo
            WHERE (p.stato = :statoCreata
                   OR (p.stato = :statoConfermata AND SIZE(p.partecipazioni) < :postiMax))
              AND p.dataPartita <= :dataFino
            """)
    List<Partita> findCandidateScadenze(@Param("statoCreata") StatoPartita statoCreata,
                                        @Param("statoConfermata") StatoPartita statoConfermata,
                                        @Param("postiMax") int postiMax,
                                        @Param("dataFino") LocalDate dataFino);

    // Partita con lock in scrittura (SELECT ... FOR UPDATE) fino alla fine della transazione:
    // serve all'ingresso, così due richieste simultanee sull'ultimo posto vengono serializzate
    // e la seconda vede la partecipazione aggiunta dalla prima
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Partita p WHERE p.id = :id")
    Optional<Partita> findByIdPerAggiornamento(@Param("id") Integer id);
}
