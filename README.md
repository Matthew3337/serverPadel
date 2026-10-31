# Padel Booking API

Backend Spring Boot per l'app di prenotazione campi da padel.

## Setup

1. Importa il progetto in STS: **File → Import → Maven → Existing Maven Projects**, seleziona questa cartella.
2. Crea il database eseguendo [sql/creazione_database.sql](sql/creazione_database.sql) su MySQL (crea `padel_booking` e le tabelle).
3. Apri `src/main/resources/application.properties` e verifica `spring.datasource.username` / `spring.datasource.password`.
4. Avvia con **Run As → Spring Boot App**.

### Deploy della versione con lo schema partita/partecipazione

Questa versione sostituisce la tabella `prenotazione` con `partita` e `partecipazione` (più `impostazioni`, `notifica_push`, `avviso`) e non include uno script di migrazione dei dati. **Prima del deploy il database di produzione va ricreato da zero** con [sql/creazione_database.sql](sql/creazione_database.sql): con `ddl-auto=validate` il server non parte su un database con lo schema precedente. Ricreare il database cancella utenti, campi e prenotazioni esistenti.

### Deploy automatico e migrazioni

Ogni push su `main` avvia [.github/workflows/deploy.yml](.github/workflows/deploy.yml), che costruisce l'immagine Docker, la pubblica su ghcr.io e la installa sulla VM di produzione. Prima di riavviare il container il workflow copia `sql/*.sql` sulla VM in `~/padel-sql/` e applica al database `padel_booking` le migrazioni `sql/AAAA-MM-GG_descrizione.sql` non ancora eseguite, in ordine alfabetico. Le migrazioni applicate sono registrate nella tabella `schema_migrazioni`. Se un pull o una migrazione falliscono il deploy si ferma e il container in esecuzione non viene toccato. Le migrazioni non vanno quindi applicate a mano; `sql/creazione_database.sql` non viene mai eseguito dal deploy.

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
- `POST /api/prenotazioni` — crea una partita (stato `creata`, durata 90 minuti) il cui creatore e unico giocatore iniziale è l'utente autenticato; gli altri giocatori entreranno tramite inviti o bacheca pubblica. Body: `idCampo`, `dataPrenotazione`, `oraInizio`, `tipo` (obbligatorio, `"PUBBLICA"` o `"PRIVATA"`); eventuali `telefonoGiocatore1..4` vengono ignorati. Risposta 201. Errori: 400 validazione, 404 campo inesistente, 409 slot occupato / fuori orario di apertura / slot nel passato
- `DELETE /api/prenotazioni/{id}` — annulla la partita (stato `annullata`, la riga resta e lo slot torna libero): creatore almeno 24h prima dell'inizio, admin sempre. Partita già annullata o già giocata: 409
- `GET /api/prenotazioni/prossima?telefono=3331234567` — prossima partita non annullata in cui l'utente gioca, dato il telefono
- `GET /api/prenotazioni/utente/{telefono}` — partite non annullate in cui l'utente gioca

Nella risposta delle prenotazioni `id` è l'id della partita, `telefonoGiocatore1` il creatore, `telefonoGiocatore2..4` gli altri giocatori (null se mancano), `dataPrenotazione` la data della partita e `stato` lo stato in minuscolo (`creata`, `confermata`, `annullata`, `giocata`) e `tipo` il tipo della partita (`PUBBLICA` o `PRIVATA`).

### Partite (richiedono header `Authorization: Bearer <token>`)
- `GET /api/partite/{id}` — dettaglio della partita: `id`, `idCampo`, `nomeCampo`, `dataPartita`, `oraInizio`, `oraFine`, `stato` (minuscolo), `tipo` (`PUBBLICA`/`PRIVATA`), `telefonoCreatore`, `posti` e `azioni`. Risposta 200.
  - Visibilità: partecipanti e admin la vedono sempre (anche annullata); una partita `PUBBLICA` non annullata è visibile anche a chi ha un livello che differisce al massimo di 1 da quello del creatore. Negli altri casi 404 con lo stesso messaggio della partita inesistente (`Partita non trovata con id {id}`).
  - `posti`: solo i posti occupati, il creatore per primo; ogni posto ha `tipo` (`UTENTE`/`CAMPO`), `telefono`, `nome`, `cognome`, `livello` (null per i posti `CAMPO`) e `confermata`.
  - `azioni` (calcolate per l'utente del token e per l'istante attuale, sull'inizio effettivo della partita anche dopo mezzanotte): `puoInvitare` (creatore, stato creata, più di 24h all'inizio, meno di 4 posti occupati), `puoEliminare` (creatore, stato creata, più di 24h all'inizio), `puoUscire` (altro partecipante, stato creata, più di 24h all'inizio), `puoConfermare` (partecipante non ancora confermato, stato creata, tra 24h e 12h dall'inizio), `puoRegistrareRisultato` (partecipante, stato confermata, dalla fine fino a 24h dopo). Per l'admin sono tutte false.
- `GET /api/partite/pubbliche` — bacheca delle partite in cui l'utente può entrare: tipo `PUBBLICA`, stato `creata`, meno di 4 posti occupati, più di 24h all'inizio effettivo, livello del creatore che differisce al massimo di 1 da quello dell'utente, utente non già partecipante. Ordinate per inizio effettivo crescente. Risposta 200: `[ { id, idCampo, nomeCampo, dataPartita, oraInizio, oraFine, nomeCreatore, cognomeCreatore, livelloCreatore, postiLiberi } ]`, `[]` se non ce ne sono. Per l'admin sempre `[]`.
- `GET /api/partite/storico` — storico dell'utente: le partite in cui ha un posto come giocatore (`UTENTE`), future e passate, escluse le `annullata`. Ordinate per inizio effettivo decrescente (dalla più recente; a parità di inizio, id decrescente). Per l'admin sempre `[]`. Risposta 200: `[ { id, idCampo, nomeCampo, dataPartita, oraInizio, oraFine, stato, tipo, risultato } ]`, `[]` se non ce ne sono. Nessun errore specifico (403 senza token).
  - `risultato` è `null` finché non ci sono almeno i primi due set; altrimenti `{ set: [ { squadra1, squadra2 } ], squadraUtente, esito, puntiAssegnati }`.
  - `set`: solo i set giocati (2 o 3), con i game delle colonne `setN_sq1`/`setN_sq2`.
  - `squadraUtente` (1 o 2) è `partecipazione.squadra`; `esito` (`VINTA`/`PERSA`, dal punto di vista dell'utente) lo dà la squadra che ha vinto più set. Entrambi `null` se l'utente non ha una squadra.
  - `puntiAssegnati` è `partecipazione.punti_assegnati`, `null` finché il punteggio non è calcolato.

  Partecipazione, partita e campo si leggono con una sola query (`JOIN FETCH`), senza una query per riga.
- `POST /api/partite/{id}/entra` — senza body: l'utente entra subito nella partita come giocatore (non invitato, non confermato). Risposta 200 con lo stesso dettaglio di `GET /api/partite/{id}`, già aggiornato. Errori:
  - 404 `Partita non trovata con id {id}` se la partita non esiste o l'utente non la vedrebbe (privata, livello fuori range, annullata);
  - 409 `La partita non è più aperta` (stato diverso da `creata`), `La partita è al completo`, `L'ingresso è chiuso: mancano meno di 24 ore all'inizio`, `Partecipi già a questa partita`;
  - 403 `L'amministratore non può entrare nelle partite`.

  La partita viene letta con un lock in scrittura (`SELECT ... FOR UPDATE`) per tutta la transazione, quindi due ingressi simultanei sull'ultimo posto non portano a 5 giocatori; il vincolo unico `uk_partecipazione_utente` impedisce in ogni caso il doppio posto dello stesso utente.
- `DELETE /api/partite/{id}` — il creatore elimina la partita: lo stato passa ad `annullata` per tutti (la riga resta e lo slot torna libero). Risposta 204 senza body. Errori:
  - 404 `Partita non trovata con id {id}` se la partita non esiste o l'utente non la vede (stessa visibilità del dettaglio);
  - 403 `Solo il creatore può eliminare la partita`, `L'amministratore non può eliminare le partite da qui`;
  - 409 `La partita non è più aperta` (stato diverso da `creata`), `L'eliminazione è chiusa: mancano meno di 24 ore all'inizio. Solo l'amministratore può annullare la partita`.
- `POST /api/partite/{id}/esci` — senza body: un partecipante diverso dal creatore esce dalla partita; il posto si libera e lo stato non cambia (una pubblica torna in bacheca). Risposta 204 senza body. Errori:
  - 404 `Partita non trovata con id {id}` se la partita non esiste o l'utente non la vede;
  - 403 `L'amministratore non può uscire dalle partite`;
  - 409 `Non partecipi a questa partita`, `Il creatore non può uscire dalla partita: può eliminarla`, `La partita non è più aperta` (stato diverso da `creata`), `L'uscita è chiusa: mancano meno di 24 ore all'inizio`.

  Le condizioni sono le stesse di `puoEliminare` e `puoUscire` del dettaglio (stesso codice nel service), con il limite stretto: a esattamente 24h dall'inizio effettivo l'operazione è già chiusa. Entrambe leggono la partita con lo stesso lock in scrittura di `entra`.

## Esempio di body per creare una prenotazione

```json
{
  "idCampo": 1,
  "dataPrenotazione": "2026-07-10",
  "oraInizio": "10:00",
  "tipo": "PUBBLICA"
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
