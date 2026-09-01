package com.padelbooking.api.repository;

import com.padelbooking.api.model.Utente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UtenteRepository extends JpaRepository<Utente, Integer> {

    Optional<Utente> findByTelefono(String telefono);

    boolean existsByTelefono(String telefono);
}
