-- Aggiunge la colonna necessaria per invalidare i JWT al login successivo.
-- Da eseguire manualmente sul database, perché il progetto usa
-- spring.jpa.hibernate.ddl-auto=validate (Hibernate non modifica mai lo schema da solo).

ALTER TABLE utente
    ADD COLUMN token_version INT NOT NULL DEFAULT 0;
