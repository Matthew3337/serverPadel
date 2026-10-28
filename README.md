# Padel Booking API

Backend Spring Boot per l'app di prenotazione campi da padel.

## Setup

1. Importa il progetto in STS: **File → Import → Maven → Existing Maven Projects**, seleziona questa cartella.
2. Crea il database eseguendo [sql/creazione_database.sql](sql/creazione_database.sql) su MySQL (crea `padel_booking` e le tabelle).
3. Apri `src/main/resources/application.properties` e verifica `spring.datasource.username` / `spring.datasource.password`.
4. Avvia con **Run As → Spring Boot App**.

### Deploy della versione con lo schema partita/partecipazione

Questa versione sostituisce la tabella `prenotazione` con `partita` e `partecipazione` (più `impostazioni`, `notifica_push`, `avviso`) e non include uno script di migrazione dei dati. **Prima del deploy il database di produzione va ricreato da zero** con [sql/creazione_database.sql](sql/creazione_database.sql): con `ddl-auto=validate` il server non parte su un database con lo schema precedente. Ricreare il database cancella utenti, campi e prenotazioni esistenti.

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
- `GET /api/prenotazioni/slot-disponibili?idCampo=1&data=2026-07-10` — uno slot è occupato se si sovrappone a una partita non annullata
- `POST /api/prenotazioni` — crea una partita (stato `creata`) con il giocatore 1 come creatore; i giocatori 2-4 indicati entrano direttamente nella partita. Campo opzionale `tipo`: `"PUBBLICA"` o `"PRIVATA"` (default `PRIVATA`). Slot già occupato: 409
- `DELETE /api/prenotazioni/{id}` — annulla la partita (stato `annullata`, la riga resta e lo slot torna libero): creatore almeno 24h prima dell'inizio, admin sempre. Partita già annullata o già giocata: 409
- `GET /api/prenotazioni/prossima?telefono=3331234567` — prossima partita non annullata in cui l'utente gioca, dato il telefono
- `GET /api/prenotazioni/utente/{telefono}` — partite non annullate in cui l'utente gioca

Nella risposta delle prenotazioni `id` è l'id della partita, `telefonoGiocatore1` il creatore, `telefonoGiocatore2..4` gli altri giocatori (null se mancano), `dataPrenotazione` la data della partita e `stato` lo stato in minuscolo (`creata`, `confermata`, `annullata`, `giocata`).

## Esempio di body per creare una prenotazione

```json
{
  "idCampo": 1,
  "telefonoGiocatore1": "3331234567",
  "telefonoGiocatore2": "3339876543",
  "telefonoGiocatore3": null,
  "telefonoGiocatore4": null,
  "dataPrenotazione": "2026-07-10",
  "oraInizio": "10:00:00",
  "tipo": "PRIVATA"
}
```

## Note architetturali

- `controller` → riceve la richiesta HTTP, delega al `service`
- `service` → contiene la business logic (equivalente ai tuoi use case Flutter): calcolo slot, verifica disponibilità, regole di cancellazione
- `repository` → accesso ai dati via Spring Data JPA (equivalente ai DAO)
- `dto` → oggetti di scambio con il client, le Entity non vengono mai esposte direttamente
- `security` → JWT + Spring Security per autenticazione/autorizzazione

## Chiave utente e JWT

- `utente.telefono` è la chiave primaria. La partita collega il creatore tramite `telefono_creatore`, e ogni giocatore ha una riga in `partecipazione` con `telefono_utente`.
- Il JWT non contiene una scadenza né dati variabili: un nuovo login dello stesso utente restituisce lo stesso token, finché `JWT_SECRET` non viene cambiato.
