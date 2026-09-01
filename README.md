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
- `GET /api/prenotazioni/mie` — storico dell'utente autenticato
- `GET /api/prenotazioni/utente/{idUtente}` — storico di un utente specifico

## Esempio di body per creare una prenotazione

```json
{
  "idCampo": 1,
  "idGiocatore1": 2,
  "idGiocatore2": 3,
  "idGiocatore3": null,
  "idGiocatore4": null,
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
