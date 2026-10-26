-- Crea da zero il database padel_booking con lo schema attuale.
-- Le tabelle rispecchiano le Entity in model/ (Utente, Campo, Prenotazione): il server usa
-- spring.jpa.hibernate.ddl-auto=validate, quindi se una colonna non corrisponde l'avvio fallisce.
-- Da eseguire su un MySQL vuoto, prima di avviare il server. Gli script datati in questa
-- cartella servono solo a migrare database creati con schemi precedenti.

CREATE DATABASE IF NOT EXISTS padel_booking
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE padel_booking;

-- Il telefono è la chiave dell'utente: le prenotazioni lo usano per collegare i giocatori.
-- La password contiene l'hash BCrypt, mai il testo in chiaro.
CREATE TABLE utente (
    telefono     VARCHAR(20)  NOT NULL,
    nome         VARCHAR(50)  NOT NULL,
    cognome      VARCHAR(50)  NOT NULL,
    password     VARCHAR(255) NOT NULL,
    data_nascita DATE         NOT NULL,
    is_admin     BOOLEAN      NOT NULL DEFAULT FALSE,
    livello      DOUBLE       NOT NULL DEFAULT 0,
    PRIMARY KEY (telefono)
) ENGINE = InnoDB;

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

-- Solo il giocatore 1 è obbligatorio, gli altri tre si possono aggiungere dopo.
-- Una prenotazione cancellata viene eliminata dalla tabella, quindi ogni riga è una prenotazione attiva.
-- Il vincolo unico impedisce due prenotazioni sullo stesso slot anche se due richieste arrivano
-- insieme, oltre al controllo che il service fa già prima di salvare.
CREATE TABLE prenotazione (
    id                  INT         NOT NULL AUTO_INCREMENT,
    id_campo            INT         NOT NULL,
    telefono_giocatore1 VARCHAR(20) NOT NULL,
    telefono_giocatore2 VARCHAR(20) NULL,
    telefono_giocatore3 VARCHAR(20) NULL,
    telefono_giocatore4 VARCHAR(20) NULL,
    data_prenotazione   DATE        NOT NULL,
    ora_inizio          TIME        NOT NULL,
    ora_fine            TIME        NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_prenotazione_slot (id_campo, data_prenotazione, ora_inizio),
    CONSTRAINT fk_prenotazione_campo
        FOREIGN KEY (id_campo) REFERENCES campo (id),
    CONSTRAINT fk_prenotazione_giocatore1_telefono
        FOREIGN KEY (telefono_giocatore1) REFERENCES utente (telefono),
    CONSTRAINT fk_prenotazione_giocatore2_telefono
        FOREIGN KEY (telefono_giocatore2) REFERENCES utente (telefono),
    CONSTRAINT fk_prenotazione_giocatore3_telefono
        FOREIGN KEY (telefono_giocatore3) REFERENCES utente (telefono),
    CONSTRAINT fk_prenotazione_giocatore4_telefono
        FOREIGN KEY (telefono_giocatore4) REFERENCES utente (telefono)
) ENGINE = InnoDB;
