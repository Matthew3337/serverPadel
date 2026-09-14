-- Migrazione MySQL per lo schema precedente, nel quale utente.id era la chiave primaria
-- e prenotazione.id_giocatore[1..4] la referenziava.
-- Eseguire un backup prima della migrazione. Non eseguire mentre l'applicazione è attiva.

DELIMITER //

-- I nomi delle FK dipendono da come è stato creato il database; le rimuoviamo
-- leggendo il catalogo, senza doverli indovinare.
CREATE PROCEDURE drop_prenotazione_utente_foreign_keys()
BEGIN
    DECLARE done BOOLEAN DEFAULT FALSE;
    DECLARE fk_name VARCHAR(64);
    DECLARE fk_cursor CURSOR FOR
        SELECT DISTINCT constraint_name
        FROM information_schema.key_column_usage
        WHERE table_schema = DATABASE()
          AND table_name = 'prenotazione'
          AND referenced_table_name = 'utente';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    OPEN fk_cursor;
    fk_loop: LOOP
        FETCH fk_cursor INTO fk_name;
        IF done THEN LEAVE fk_loop; END IF;
        SET @sql_statement = CONCAT('ALTER TABLE prenotazione DROP FOREIGN KEY `', fk_name, '`');
        PREPARE statement FROM @sql_statement;
        EXECUTE statement;
        DEALLOCATE PREPARE statement;
    END LOOP;
    CLOSE fk_cursor;
END //

CALL drop_prenotazione_utente_foreign_keys() //
DROP PROCEDURE drop_prenotazione_utente_foreign_keys //

DELIMITER ;

-- Creiamo le nuove colonne e copiamo i riferimenti esistenti prima di eliminare gli ID numerici.
ALTER TABLE prenotazione
    ADD COLUMN telefono_giocatore1 VARCHAR(20) NULL,
    ADD COLUMN telefono_giocatore2 VARCHAR(20) NULL,
    ADD COLUMN telefono_giocatore3 VARCHAR(20) NULL,
    ADD COLUMN telefono_giocatore4 VARCHAR(20) NULL;

UPDATE prenotazione p
LEFT JOIN utente u1 ON u1.id = p.id_giocatore1
LEFT JOIN utente u2 ON u2.id = p.id_giocatore2
LEFT JOIN utente u3 ON u3.id = p.id_giocatore3
LEFT JOIN utente u4 ON u4.id = p.id_giocatore4
SET p.telefono_giocatore1 = u1.telefono,
    p.telefono_giocatore2 = u2.telefono,
    p.telefono_giocatore3 = u3.telefono,
    p.telefono_giocatore4 = u4.telefono;

-- Questa verifica deve restituire 0: in caso contrario interrompere la migrazione e correggere i dati.
SELECT COUNT(*) AS prenotazioni_senza_giocatore1 FROM prenotazione WHERE telefono_giocatore1 IS NULL;

ALTER TABLE prenotazione
    MODIFY telefono_giocatore1 VARCHAR(20) NOT NULL,
    DROP COLUMN id_giocatore1,
    DROP COLUMN id_giocatore2,
    DROP COLUMN id_giocatore3,
    DROP COLUMN id_giocatore4;

-- Se token_version non è mai stato aggiunto, rimuovere la riga seguente.
ALTER TABLE utente DROP COLUMN token_version;
ALTER TABLE utente DROP PRIMARY KEY, DROP COLUMN id, ADD PRIMARY KEY (telefono);

ALTER TABLE prenotazione
    ADD CONSTRAINT fk_prenotazione_giocatore1_telefono
        FOREIGN KEY (telefono_giocatore1) REFERENCES utente(telefono),
    ADD CONSTRAINT fk_prenotazione_giocatore2_telefono
        FOREIGN KEY (telefono_giocatore2) REFERENCES utente(telefono),
    ADD CONSTRAINT fk_prenotazione_giocatore3_telefono
        FOREIGN KEY (telefono_giocatore3) REFERENCES utente(telefono),
    ADD CONSTRAINT fk_prenotazione_giocatore4_telefono
        FOREIGN KEY (telefono_giocatore4) REFERENCES utente(telefono);
