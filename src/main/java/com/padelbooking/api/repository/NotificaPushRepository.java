package com.padelbooking.api.repository;

import com.padelbooking.api.model.NotificaPush;
import com.padelbooking.api.model.Utente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificaPushRepository extends JpaRepository<NotificaPush, Integer> {

    // Eliminazione dell'account (T18): le notifiche ricevute dall'utente vanno cancellate prima della sua riga,
    // che telefono_destinatario referenzia. Un'unica DELETE invece di una per notifica
    @Modifying
    @Query("DELETE FROM NotificaPush n WHERE n.destinatario = :destinatario")
    int deleteByDestinatario(@Param("destinatario") Utente destinatario);
}
