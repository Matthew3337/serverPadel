package com.padelbooking.api.repository;

import com.padelbooking.api.model.Avviso;
import com.padelbooking.api.model.StatoPartita;
import com.padelbooking.api.model.TipoAvviso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AvvisoRepository extends JpaRepository<Avviso, Integer> {

    // Il vincolo uk_avviso (id_partita, tipo) ammette un solo avviso per tipo e partita:
    // l'attività pianificata controlla prima di inserire, così ogni giro è idempotente
    boolean existsByPartitaIdAndTipo(Integer idPartita, TipoAvviso tipo);

    // Candidati per la bacheca avvisi (RF_GA_7–9): gli avvisi delle partite con stato diverso da quello
    // indicato (si passa ANNULLATA) e con data_partita da una data in poi. Partita, campo e posti in
    // JOIN FETCH perché servono a ogni riga. Il filtro preciso sull'inizio effettivo lo fa il service.
    @Query("""
            SELECT a FROM Avviso a
            JOIN FETCH a.partita p
            JOIN FETCH p.campo
            LEFT JOIN FETCH p.partecipazioni
            WHERE p.stato <> :statoEscluso
              AND p.dataPartita >= :dataDa
            """)
    List<Avviso> findAperti(@Param("statoEscluso") StatoPartita statoEscluso, @Param("dataDa") LocalDate dataDa);
}
