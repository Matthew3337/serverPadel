-- Crea l'utente amministratore dedicato (login con telefono 0000000000).
-- La password è salvata solo come hash BCrypt; il testo in chiaro non è nel repository.
-- Livello e punteggio restano ai default: l'admin è escluso da classifica, inviti e bacheca.
INSERT INTO utente (telefono, nome, cognome, password, data_nascita, is_admin)
VALUES ('0000000000', 'Admin', 'Padel',
        '$2a$10$CTQ8EtVLyNHpVRI0DWKYg.gFC4N9KOUuQ2nTDqU01f8Hj0YXcil.K',
        '2000-01-01', TRUE);
