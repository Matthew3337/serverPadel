-- Crea da zero il database padel_booking con lo schema target derivato dal RAD (docs/RAD.md §3.4.3).
-- Le Entity Java in model/ vanno allineate a questo schema: il server usa
-- spring.jpa.hibernate.ddl-auto=validate, quindi se una colonna non corrisponde l'avvio fallisce.
-- Da eseguire su un MySQL vuoto (8.0.16 o successivo, perché i vincoli CHECK siano applicati),
-- prima di avviare il server.

CREATE DATABASE IF NOT EXISTS padel_booking
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE padel_booking;

-- Il telefono è la chiave dell'utente: partite, partecipazioni e notifiche lo usano per collegarlo.
-- La password contiene l'hash BCrypt, mai il testo in chiaro.
-- Il livello va da 0 a 5 a passi di mezzo punto; il punteggio non scende mai sotto zero.
CREATE TABLE utente (
    telefono     VARCHAR(20)  NOT NULL,
    nome         VARCHAR(50)  NOT NULL,
    cognome      VARCHAR(50)  NOT NULL,
    password     VARCHAR(255) NOT NULL,
    data_nascita DATE         NOT NULL,
    is_admin     BOOLEAN      NOT NULL DEFAULT FALSE,
    livello      DOUBLE       NOT NULL DEFAULT 1,
    punteggio    INT          NOT NULL DEFAULT 0,
    fcm_token    VARCHAR(255) NULL,
    PRIMARY KEY (telefono),
    CONSTRAINT ck_utente_livello   CHECK (livello BETWEEN 0 AND 5 AND livello * 2 = FLOOR(livello * 2)),
    CONSTRAINT ck_utente_punteggio CHECK (punteggio >= 0)
) ENGINE = InnoDB;

-- Una sola riga: il livello unico dei giocatori campo (RF_GA_14).
-- Il vincolo su id impedisce di inserirne una seconda.
CREATE TABLE impostazioni (
    id                      TINYINT NOT NULL DEFAULT 1,
    livello_giocatori_campo DOUBLE  NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    CONSTRAINT ck_impostazioni_singola CHECK (id = 1),
    CONSTRAINT ck_impostazioni_livello CHECK (livello_giocatori_campo BETWEEN 0 AND 5
                                              AND livello_giocatori_campo * 2 = FLOOR(livello_giocatori_campo * 2))
) ENGINE = InnoDB;

INSERT INTO impostazioni (id) VALUES (1);

-- ora_chiusura può essere minore di ora_apertura quando il campo chiude dopo mezzanotte,
-- per questo non c'è un vincolo tra i due orari.
CREATE TABLE campo (
    id           INT          NOT NULL AUTO_INCREMENT,
    nome         VARCHAR(100) NOT NULL,
    al_coperto   BOOLEAN      NOT NULL,
    ora_apertura TIME         NOT NULL,
    ora_chiusura TIME         NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

-- La partita è la prenotazione. Le annullate restano nella tabella:
-- slot_attivo vale NULL per loro, così il vincolo unico le ignora e lo slot torna prenotabile.
-- Il vincolo unico impedisce due partite attive sullo stesso slot anche con richieste simultanee.
-- I game dei set sono NULL finché il risultato non è registrato; il terzo set resta NULL se finisce 2-0.
CREATE TABLE partita (
    id                   INT         NOT NULL AUTO_INCREMENT,
    id_campo             INT         NOT NULL,
    telefono_creatore    VARCHAR(20) NOT NULL,
    data_partita         DATE        NOT NULL,
    ora_inizio           TIME        NOT NULL,
    ora_fine             TIME        NOT NULL,
    tipo                 ENUM('PUBBLICA', 'PRIVATA') NOT NULL,
    stato                ENUM('CREATA', 'CONFERMATA', 'ANNULLATA', 'GIOCATA') NOT NULL DEFAULT 'CREATA',
    set1_sq1             TINYINT     NULL,
    set1_sq2             TINYINT     NULL,
    set2_sq1             TINYINT     NULL,
    set2_sq2             TINYINT     NULL,
    set3_sq1             TINYINT     NULL,
    set3_sq2             TINYINT     NULL,
    telefono_registrante VARCHAR(20) NULL,
    data_risultato       DATETIME    NULL,
    slot_attivo          TINYINT AS (IF(stato = 'ANNULLATA', NULL, 1)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_partita_slot (id_campo, data_partita, ora_inizio, slot_attivo),
    CONSTRAINT fk_partita_campo
        FOREIGN KEY (id_campo) REFERENCES campo (id),
    CONSTRAINT fk_partita_creatore
        FOREIGN KEY (telefono_creatore) REFERENCES utente (telefono),
    CONSTRAINT fk_partita_registrante
        FOREIGN KEY (telefono_registrante) REFERENCES utente (telefono),
    CONSTRAINT ck_partita_game CHECK (
        set1_sq1 BETWEEN 0 AND 6 AND set1_sq2 BETWEEN 0 AND 6 AND
        set2_sq1 BETWEEN 0 AND 6 AND set2_sq2 BETWEEN 0 AND 6 AND
        set3_sq1 BETWEEN 0 AND 6 AND set3_sq2 BETWEEN 0 AND 6),
    CONSTRAINT ck_partita_set CHECK (
        ((set1_sq1 IS NULL) = (set1_sq2 IS NULL)) AND ((set1_sq1 IS NULL) = (set2_sq1 IS NULL)) AND
        ((set2_sq1 IS NULL) = (set2_sq2 IS NULL)) AND ((set3_sq1 IS NULL) = (set3_sq2 IS NULL)) AND
        (set1_sq1 IS NOT NULL OR set3_sq1 IS NULL))
) ENGINE = InnoDB;

-- Un posto occupato in una partita. Il giocatore campo è una riga con tipo CAMPO e senza utente.
-- squadra (1 o 2) si valorizza quando si registra il risultato; punti_assegnati serve per lo storno.
-- Massimo 4 posti, almeno 1 utente e massimo 3 giocatori campo li controlla il service.
-- Il vincolo unico ignora le righe CAMPO (telefono NULL), quindi possono essercene più di una.
CREATE TABLE partecipazione (
    id              INT         NOT NULL AUTO_INCREMENT,
    id_partita      INT         NOT NULL,
    tipo            ENUM('UTENTE', 'CAMPO') NOT NULL,
    telefono_utente VARCHAR(20) NULL,
    squadra         TINYINT     NULL,
    invitato        BOOLEAN     NOT NULL DEFAULT FALSE,
    confermata      BOOLEAN     NOT NULL DEFAULT FALSE,
    punti_assegnati INT         NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_partecipazione_utente (id_partita, telefono_utente),
    CONSTRAINT fk_partecipazione_partita
        FOREIGN KEY (id_partita) REFERENCES partita (id) ON DELETE CASCADE,
    CONSTRAINT fk_partecipazione_utente
        FOREIGN KEY (telefono_utente) REFERENCES utente (telefono),
    CONSTRAINT ck_partecipazione_squadra CHECK (squadra IN (1, 2)),
    CONSTRAINT ck_partecipazione_tipo CHECK (
        (tipo = 'UTENTE' AND telefono_utente IS NOT NULL) OR
        (tipo = 'CAMPO'  AND telefono_utente IS NULL AND confermata = FALSE
                         AND invitato = FALSE AND punti_assegnati IS NULL))
) ENGINE = InnoDB;

-- Notifiche push inviate, tutte relative a una partita.
-- Il vincolo unico impedisce allo scheduler di mandare due volte la stessa.
CREATE TABLE notifica_push (
    id                    INT         NOT NULL AUTO_INCREMENT,
    telefono_destinatario VARCHAR(20) NOT NULL,
    id_partita            INT         NOT NULL,
    tipo                  ENUM('INVITO', 'RICHIESTA_CONFERMA', 'ANNULLAMENTO', 'PROMEMORIA',
                               'ADMIN_CREAZIONE', 'ADMIN_ANNULLAMENTO', 'ADMIN_CONFERMA',
                               'ADMIN_INCOMPLETA') NOT NULL,
    data_invio            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notifica (telefono_destinatario, id_partita, tipo),
    CONSTRAINT fk_notifica_destinatario
        FOREIGN KEY (telefono_destinatario) REFERENCES utente (telefono),
    CONSTRAINT fk_notifica_partita
        FOREIGN KEY (id_partita) REFERENCES partita (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- Bacheca avvisi dell'amministratore (oggi solo partite incomplete a -24 h).
-- Il vincolo unico impedisce di creare due volte lo stesso avviso per una partita.
CREATE TABLE avviso (
    id             INT      NOT NULL AUTO_INCREMENT,
    id_partita     INT      NOT NULL,
    tipo           ENUM('PARTITA_INCOMPLETA') NOT NULL,
    data_creazione DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    letto          BOOLEAN  NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_avviso (id_partita, tipo),
    CONSTRAINT fk_avviso_partita
        FOREIGN KEY (id_partita) REFERENCES partita (id) ON DELETE CASCADE
) ENGINE = InnoDB;
