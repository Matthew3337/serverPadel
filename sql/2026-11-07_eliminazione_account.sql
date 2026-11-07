-- Eliminazione dell'account (T18). Due ALTER TABLE, ognuna atomica: se la seconda fallisse il deploy si
-- ferma senza registrare la migrazione, e al giro successivo la prima si riesegue senza effetti.
--
-- Una partita non ancora finita il cui creatore elimina l'account passa a un altro admin; se non ce ne sono,
-- e nelle partite già finite o annullate, il creatore resta vuoto.
ALTER TABLE partita
    MODIFY telefono_creatore VARCHAR(20) NULL;

-- Nelle partite già finite o annullate il posto di chi elimina l'account resta, di tipo UTENTE ma senza
-- telefono ("utente eliminato"), con squadra e punti invariati. Per CAMPO non cambia nulla.
-- Il vincolo si toglie e si rimette nella stessa istruzione, così non resta mai una tabella senza vincolo.
ALTER TABLE partecipazione
    DROP CHECK ck_partecipazione_tipo,
    ADD CONSTRAINT ck_partecipazione_tipo CHECK (
        (tipo = 'UTENTE') OR
        (tipo = 'CAMPO'  AND telefono_utente IS NULL AND confermata = FALSE
                         AND invitato = FALSE AND punti_assegnati IS NULL));
