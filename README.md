# Padel Booking API

Backend Spring Boot per l'app di prenotazione campi da padel.

## Setup

1. Importa il progetto in STS: **File → Import → Maven → Existing Maven Projects**, seleziona questa cartella.
2. Esegui gli script SQL già preparati (creazione tabelle + seed campi/admin) sul tuo database `padel_booking`.
3. Apri `src/main/resources/application.properties` e verifica `spring.datasource.username` / `spring.datasource.password`.
4. Avvia con **Run As → Spring Boot App**.

L'utente admin inserito via seed ha come password l'hash placeholder presente nello script SQL: **va sostituito** con un hash BCrypt reale prima di poter fare login. Il modo più semplice è registrare un utente normale tramite `/api/auth/register`, poi aggiornare manualmente `is_admin = 1` per quell'utente via query SQL, invece di usare l'hash placeholder.

## Endpoint disponibili

### Autenticazione (pubblici)
- `POST /api/auth/register` — registra un nuovo utente
- `POST /api/auth/login` — restituisce un token JWT

### Campi
- `GET /api/campi` — pubblico, elenco campi
- `GET /api/campi/{id}` — pubblico, dettaglio campo
- `POST /api/campi` — solo ADMIN
- `PUT /api/campi/{id}` — solo ADMIN
- `DELETE /api/campi/{id}` — solo ADMIN

### Prenotazioni (richiedono header `Authorization: Bearer <token>`)
- `GET /api/prenotazioni/slot-disponibili?idCampo=1&data=2026-07-10` — slot da 90 min liberi/occupati
- `POST /api/prenotazioni` — crea una prenotazione
- `DELETE /api/prenotazioni/{id}` — cancella (proprietario entro 24h, admin sempre)
- `GET /api/prenotazioni/prossima?telefono=3331234567` — prossima prenotazione di un utente, dato il telefono
- `GET /api/prenotazioni/utente/{telefono}` — storico di un utente specifico

## Esempio di body per creare una prenotazione

```json
{
  "idCampo": 1,
  "telefonoGiocatore1": "3331234567",
  "telefonoGiocatore2": "3339876543",
  "telefonoGiocatore3": null,
  "telefonoGiocatore4": null,
  "dataPrenotazione": "2026-07-10",
  "oraInizio": "10:00:00"
}
```

## Note architetturali

- `controller` → riceve la richiesta HTTP, delega al `service`
- `service` → contiene la business logic (equivalente ai tuoi use case Flutter): calcolo slot, verifica disponibilità, regole di cancellazione
- `repository` → accesso ai dati via Spring Data JPA (equivalente ai DAO)
- `dto` → oggetti di scambio con il client, le Entity non vengono mai esposte direttamente
- `security` → JWT + Spring Security per autenticazione/autorizzazione

## Chiave utente e JWT

- `utente.telefono` è la chiave primaria. Le prenotazioni collegano i giocatori tramite `telefono_giocatore1` ... `telefono_giocatore4`.
- Il JWT non contiene una scadenza né dati variabili: un nuovo login dello stesso utente restituisce lo stesso token, finché `JWT_SECRET` non viene cambiato.
- Per trasformare un database già esistente eseguire [sql/2026-09-14_utente_telefono_primary_key.sql](sql/2026-09-14_utente_telefono_primary_key.sql), dopo un backup. Se il database non ha la colonna `token_version`, rimuovere dalla migrazione la sola istruzione che la elimina.
