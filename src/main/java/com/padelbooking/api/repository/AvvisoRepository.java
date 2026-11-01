package com.padelbooking.api.repository;

import com.padelbooking.api.model.Avviso;
import com.padelbooking.api.model.TipoAvviso;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvvisoRepository extends JpaRepository<Avviso, Integer> {

    // Il vincolo uk_avviso (id_partita, tipo) ammette un solo avviso per tipo e partita:
    // l'attività pianificata controlla prima di inserire, così ogni giro è idempotente
    boolean existsByPartitaIdAndTipo(Integer idPartita, TipoAvviso tipo);
}
