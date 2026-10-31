package com.padelbooking.api.repository;

import com.padelbooking.api.model.Partecipazione;
import com.padelbooking.api.model.StatoPartita;
import com.padelbooking.api.model.TipoPartecipazione;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PartecipazioneRepository extends JpaRepository<Partecipazione, Integer> {

    // Storico (RF_GP_18): le partecipazioni UTENTE dell'utente nelle partite con stato diverso da quello
    // indicato (si passa ANNULLATA). Si parte dalla partecipazione perché porta squadra e punti
    // dell'utente; partita e campo sono in JOIN FETCH, così l'intero storico si legge con una sola query
    // invece di una per riga. Il vincolo uk_partecipazione_utente garantisce una riga per partita.
    // L'ordine sull'inizio effettivo lo fa il service.
    @Query("""
            SELECT pa FROM Partecipazione pa
            JOIN FETCH pa.partita p
            JOIN FETCH p.campo
            WHERE pa.tipo = :tipoUtente
              AND pa.utente.telefono = :telefono
              AND p.stato <> :statoEscluso
            """)
    List<Partecipazione> findStoricoByTelefono(@Param("telefono") String telefono,
                                               @Param("tipoUtente") TipoPartecipazione tipoUtente,
                                               @Param("statoEscluso") StatoPartita statoEscluso);
}
