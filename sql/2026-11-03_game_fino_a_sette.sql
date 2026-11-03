-- I set possono finire 7-5 o 7-6: ck_partita_game passa da 0-6 a 0-7 game per squadra.
-- La regola precisa sui punteggi validi sta nel server (RegoleRisultatoPartita.rifiutoSet).
-- Un solo ALTER: MySQL non annulla le DDL in caso di errore, così non può restare senza vincolo.
-- Tutti i risultati già salvati rispettano il nuovo vincolo, che è più largo del precedente.
ALTER TABLE partita
    DROP CHECK ck_partita_game,
    ADD CONSTRAINT ck_partita_game CHECK (
        set1_sq1 BETWEEN 0 AND 7 AND set1_sq2 BETWEEN 0 AND 7 AND
        set2_sq1 BETWEEN 0 AND 7 AND set2_sq2 BETWEEN 0 AND 7 AND
        set3_sq1 BETWEEN 0 AND 7 AND set3_sq2 BETWEEN 0 AND 7);
