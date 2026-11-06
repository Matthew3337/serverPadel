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

L'utente admin di produzione (telefono `0000000000`) è creato dalla migrazione `sql/2026-11-06_utente_admin.sql`, che contiene solo l'hash BCrypt della password. `sql/creazione_database.sql` non crea admin: su un database nuovo si registra un utente con `/api/auth/register` e poi si imposta `is_admin = 1` via SQL. La registrazione crea sempre utenti non admin.

## Endpoint disponibili

### Autenticazione (pubblici)
- `POST /api/auth/register` — registra un nuovo utente
- `POST /api/auth/login` — restituisce un token JWT

### Campi
- `GET /api/campi` — pubblico, elenco campi
- `GET /api/campi/{id}` — pubblico, dettaglio campo
- `POST /api/campi` — solo ADMIN
- `PUT /api/campi/{id}` — solo ADMIN
- `DELETE /api/campi/{id}` — solo ADMIN. Risposta 204. Errori: 404 `Campo non trovato con id N`; 409 `Il campo ha partite collegate e non può essere eliminato`, se esiste almeno una partita sul campo, di qualunque stato (`partita.id_campo` è una chiave esterna). Un cambio di orario con `PUT` non tocca le partite già prenotate.

### Prenotazioni (richiedono header `Authorization: Bearer <token>`)
- `GET /api/prenotazioni/slot-disponibili?idCampo=1&data=2026-07-10` — uno slot è occupato se si sovrappone a una partita non annullata
- `POST /api/prenotazioni` — crea una partita (durata 90 minuti) il cui creatore è l'utente autenticato; gli altri giocatori entrano con gli `invitati` della richiesta, con gli inviti o dalla bacheca pubblica. Body: `idCampo`, `dataPrenotazione`, `oraInizio`, `tipo` (obbligatorio, `"PUBBLICA"` o `"PRIVATA"`), `invitati` (facoltativo, `["3330000002", ...]`); eventuali `telefonoGiocatore1..4` vengono ignorati. Risposta 201.
  - `dataPrenotazione` (la data della serata) al massimo oggi + 15 giorni: oggi + 15 è ancora prenotabile. Lo stesso limite vale per `POST /api/admin/partite`.
  - `invitati`: al massimo 3, senza duplicati e senza il creatore. Ognuno entra come con `POST /api/partite/{id}/inviti` (`invitato=true`), nella stessa transazione della partita: se un invitato non è valido non si salva nulla. I posti degli invitati seguono quello del creatore in ordine di telefono.
  - Prenotazione tardiva: da 12 ore prima dell'inizio effettivo in poi (a −12h esatte compreso), cioè a finestra di conferma chiusa, creatore e invitati nascono con `confermata=true`. Con 4 posti la partita nasce `confermata`, altrimenti resta `creata` e all'inizio l'attività pianificata la annulla. Prima delle −12h tutti nascono con `confermata=false` e la partita è `creata`.
  - Errori:
    - 400 validazione del body (`campo: messaggio`); per `invitati`, senza prefisso: `Si possono invitare al massimo 3 utenti`, `L'elenco degli invitati contiene telefoni duplicati`, `Non puoi invitare te stesso`, `Il telefono dell'invitato è obbligatorio` (elemento null o vuoto). L'elenco si controlla prima dello slot;
    - 404 campo inesistente, `Utente non trovato` (invitato);
    - 409 slot occupato / fuori orario di apertura / slot nel passato, `Si può prenotare al massimo 15 giorni in anticipo`, `Non è possibile invitare un amministratore`.
- `DELETE /api/prenotazioni/{id}` — annulla la partita (stato `annullata`, la riga resta e lo slot torna libero): creatore almeno 24h prima dell'inizio, admin sempre. Partita già annullata o già giocata: 409
- `GET /api/prenotazioni/prossima?telefono=3331234567` — prossima partita non annullata in cui l'utente gioca, dato il telefono
- `GET /api/prenotazioni/utente/{telefono}` — partite non annullate in cui l'utente gioca

Nella risposta delle prenotazioni `id` è l'id della partita, `telefonoGiocatore1` il creatore (un admin, se l'admin ha tolto il creatore dalla partita: non è tra i giocatori e, con 4 utenti, i campi mostrano solo i primi tre), `telefonoGiocatore2..4` gli altri giocatori (null se mancano), `dataPrenotazione` la data della partita e `stato` lo stato in minuscolo (`creata`, `confermata`, `annullata`, `giocata`) e `tipo` il tipo della partita (`PUBBLICA` o `PRIVATA`).

### Utenti (richiedono header `Authorization: Bearer <token>`)
- `GET /api/utenti/invitabili?cerca=<testo>` — utenti da invitare a una partita ancora da prenotare (`invitati` di `POST /api/prenotazioni`): gli utenti non admin, escluso chi chiama, di qualunque livello. `cerca` funziona come in `GET /api/partite/{id}/invitabili`; stesso ordine (cognome, nome, telefono) e stesso formato. Risposta 200: `[ { telefono, nome, cognome, livello } ]`, `[]` se non ce ne sono. Nessun errore specifico (403 senza token).

### Partite (richiedono header `Authorization: Bearer <token>`)
- `GET /api/partite/{id}` — dettaglio della partita: `id`, `idCampo`, `nomeCampo`, `dataPartita`, `oraInizio`, `oraFine`, `stato` (minuscolo), `tipo` (`PUBBLICA`/`PRIVATA`), `telefonoCreatore`, `posti`, `azioni` e `risultato`. Risposta 200.
  - Visibilità: partecipanti e admin la vedono sempre (anche annullata); una partita `PUBBLICA` non annullata è visibile anche a chi ha un livello che differisce al massimo di 1 da quello del creatore, oppure a tutti i livelli se il creatore è un admin. Negli altri casi 404 con lo stesso messaggio della partita inesistente (`Partita non trovata con id {id}`).
  - `telefonoCreatore` può essere quello di un admin, se l'admin ha tolto il creatore dalla partita (vedi `PUT /api/admin/partite/{id}/partecipanti`): l'admin non occupa un posto, quindi non compare in `posti`.
  - `posti`: solo i posti occupati, il creatore per primo se ha un posto, poi gli altri in ordine di `idPosto`; ogni posto ha `idPosto` (id della partecipazione), `tipo` (`UTENTE`/`CAMPO`), `telefono`, `nome`, `cognome`, `livello` (null per i posti `CAMPO`), `confermata` e `squadra` (1, 2 oppure null finché il risultato non è registrato).
  - `azioni` (calcolate per l'utente del token e per l'istante attuale, sull'inizio effettivo della partita anche dopo mezzanotte): `puoInvitare`, `puoEliminare` e `puoCambiareTipo` sono false per tutti se il creatore è un admin. `puoInvitare` (creatore, stato creata o confermata, prima dell'inizio, meno di 4 posti occupati), `puoCambiareTipo` (creatore, stato creata o confermata, prima dell'inizio: stesse regole di `PUT /api/partite/{id}/tipo`), `puoEliminare` (creatore, stato creata, più di 24h all'inizio), `puoUscire` (altro partecipante, stato creata, più di 24h all'inizio), `puoConfermare` (partecipante non ancora confermato, stato creata, tra 24h e 12h dall'inizio: stesse regole di `POST /api/partite/{id}/conferma`), `puoRegistrareRisultato` (partecipante, stato confermata, risultato non ancora registrato, dalla fine fino a 24h dopo: stesse regole di `POST /api/partite/{id}/risultato`). Per l'admin sono tutte false.
  - `risultato`: null finché non è registrato, altrimenti `{ set: [ { squadra1, squadra2 } ], squadraUtente, esito, puntiAssegnati }`, la stessa forma dello storico, visto dall'utente del token. Per l'admin, o per chi non ha una squadra, `squadraUtente` ed `esito` valgono null, e così `puntiAssegnati` per chi non gioca.
- `GET /api/partite/pubbliche` — bacheca delle partite in cui l'utente può entrare: tipo `PUBBLICA`, stato `creata`, meno di 4 posti occupati, più di 24h all'inizio effettivo, livello del creatore che differisce al massimo di 1 da quello dell'utente (qualsiasi livello se il creatore è un admin), utente non già partecipante. Ordinate per inizio effettivo crescente. Risposta 200: `[ { id, idCampo, nomeCampo, dataPartita, oraInizio, oraFine, nomeCreatore, cognomeCreatore, livelloCreatore, postiLiberi } ]`, `[]` se non ce ne sono. `livelloCreatore` è `null` se il creatore è un admin. Per l'admin sempre `[]`.
- `GET /api/partite/storico` — storico dell'utente: le partite in cui ha un posto come giocatore (`UTENTE`), escluse le `annullata`, già finite (adesso >= fine effettiva), comprese quelle senza risultato, che si registra aprendo il dettaglio. Quelle non ancora finite sono in `GET /api/partite/mie`. Ordinate per inizio effettivo decrescente (dalla più recente; a parità di inizio, id decrescente). Per l'admin sempre `[]`. Risposta 200: `[ { id, idCampo, nomeCampo, dataPartita, oraInizio, oraFine, stato, tipo, risultato } ]`, `[]` se non ce ne sono. Nessun errore specifico (403 senza token).
  - `risultato` è `null` finché non ci sono almeno i primi due set; altrimenti `{ set: [ { squadra1, squadra2 } ], squadraUtente, esito, puntiAssegnati }`.
  - `set`: solo i set giocati (2 o 3), con i game delle colonne `setN_sq1`/`setN_sq2`.
  - `squadraUtente` (1 o 2) è `partecipazione.squadra`; `esito` (`VINTA`/`PERSA`, dal punto di vista dell'utente) lo dà la squadra che ha vinto più set. Entrambi `null` se l'utente non ha una squadra.
  - `puntiAssegnati` è `partecipazione.punti_assegnati`, `null` finché il punteggio non è calcolato.

  Partecipazione, partita e campo si leggono con una sola query (`JOIN FETCH`), senza una query per riga.
- `GET /api/partite/mie` — le mie partite: quelle in cui l'utente ha un posto come giocatore (`UTENTE`), escluse le `annullata`, non ancora finite (adesso < fine effettiva), quindi anche quella in corso. Ordinate per inizio effettivo crescente (a parità, id crescente). Stesso formato dello storico. Per l'admin sempre `[]`. Nessun errore specifico (403 senza token). Storico e mie partite si dividono le stesse partite sul confine della fine effettiva: alla fine esatta la partita è nello storico.
- `PUT /api/partite/{id}/tipo` — body `{ "tipo": "PRIVATA" }` (`PUBBLICA` o `PRIVATA`): il creatore cambia la visibilità fino all'inizio effettivo, con la partita `creata` o `confermata`. Chi è già in partita ci resta; cambia solo chi la vede in bacheca. Lo stesso tipo di prima non è un errore. Risposta 200 con il dettaglio aggiornato. Errori, controllati in quest'ordine:
  - 403 `Solo il creatore può modificare la visibilità` per l'admin, senza leggere la partita;
  - 400 `Il tipo è obbligatorio`, `Il tipo deve essere PUBBLICA o PRIVATA` (senza prefisso del campo, anche questi senza leggere la partita);
  - 404 `Partita non trovata con id {id}` se la partita non esiste o l'utente non la vede;
  - 403 `Solo il creatore può modificare la visibilità`;
  - 409 `La partita non è più aperta` (annullata o giocata), `La partita è già iniziata` (a inizio esatto è già chiusa).

  Le condizioni sono quelle di `puoCambiareTipo` (stesso codice nel service). La partita è letta con lo stesso lock in scrittura di `entra`.
- `POST /api/partite/{id}/entra` — senza body: l'utente entra subito nella partita come giocatore (non invitato, non confermato). Risposta 200 con lo stesso dettaglio di `GET /api/partite/{id}`, già aggiornato. Errori:
  - 404 `Partita non trovata con id {id}` se la partita non esiste o l'utente non la vedrebbe (privata, livello fuori range, annullata);
  - 409 `La partita non è più aperta` (stato diverso da `creata`), `La partita è al completo`, `L'ingresso è chiuso: mancano meno di 24 ore all'inizio`, `Partecipi già a questa partita`;
  - 403 `L'amministratore non può entrare nelle partite`.

  La partita viene letta con un lock in scrittura (`SELECT ... FOR UPDATE`) per tutta la transazione, quindi due ingressi simultanei sull'ultimo posto non portano a 5 giocatori; il vincolo unico `uk_partecipazione_utente` impedisce in ogni caso il doppio posto dello stesso utente.
- `DELETE /api/partite/{id}` — il creatore elimina la partita: lo stato passa ad `annullata` per tutti (la riga resta e lo slot torna libero). Risposta 204 senza body. Errori:
  - 404 `Partita non trovata con id {id}` se la partita non esiste o l'utente non la vede (stessa visibilità del dettaglio);
  - 403 `Solo il creatore può eliminare la partita`, `L'amministratore non può eliminare le partite da qui`;
  - 409 `La partita non è più aperta` (stato diverso da `creata`), `L'eliminazione è chiusa: mancano meno di 24 ore all'inizio. Solo l'amministratore può annullare la partita`.
- `POST /api/partite/{id}/esci` — senza body: un partecipante diverso dal creatore esce dalla partita; il posto si libera e lo stato non cambia (una pubblica torna in bacheca). Se però non resta nessun partecipante `UTENTE`, anche con posti `CAMPO` rimasti, la partita passa ad `annullata` e lo slot torna libero: può capitare solo con un creatore admin, che non ha un posto. Risposta 204 senza body. Errori:
  - 404 `Partita non trovata con id {id}` se la partita non esiste o l'utente non la vede;
  - 403 `L'amministratore non può uscire dalle partite`;
  - 409 `Non partecipi a questa partita`, `Il creatore non può uscire dalla partita: può eliminarla`, `La partita non è più aperta` (stato diverso da `creata`), `L'uscita è chiusa: mancano meno di 24 ore all'inizio`.

  Le condizioni sono le stesse di `puoEliminare` e `puoUscire` del dettaglio (stesso codice nel service), con il limite stretto: a esattamente 24h dall'inizio effettivo l'operazione è già chiusa. Entrambe leggono la partita con lo stesso lock in scrittura di `entra`.
- `GET /api/partite/{id}/invitabili?cerca=<testo>` — riservato al creatore: utenti che può invitare, cioè non admin, senza un posto nella partita (escluso quindi il creatore), di qualunque livello. `cerca` è facoltativo: filtra su nome o cognome (contiene, senza distinzione tra maiuscole e minuscole; spazi ai lati ignorati; `%` e `_` cercati come caratteri normali). Ordinati per cognome, nome e telefono. Risposta 200: `[ { telefono, nome, cognome, livello } ]`, `[]` se non ce ne sono. Errori:
  - 404 `Partita non trovata con id {id}` se la partita non esiste o l'utente non la vede;
  - 403 `Solo il creatore può invitare` (anche per l'admin, senza leggere la partita).
- `POST /api/partite/{id}/inviti` — body `{ "telefono": "3330000002" }`: il creatore aggiunge subito l'utente alla partita, pubblica o privata, come giocatore invitato (`invitato=true`), senza accettazione dell'invitato, fino all'inizio effettivo. L'invitato ha `confermata=false`, tranne a finestra di conferma chiusa (da −12h in poi) o in una partita già `confermata` con un posto libero, dove nasce con `confermata=true`. Dopo l'invito, una partita `creata` con 4 posti e tutti gli utenti confermati passa a `confermata`; se resta un utente entrato prima delle −12h e non confermato, resta `creata` e all'inizio viene annullata. Risposta 200 con il dettaglio di `GET /api/partite/{id}` aggiornato (visto dal creatore). Errori:
  - 400 `telefono: Il telefono è obbligatorio` (mancante o vuoto);
  - 404 `Partita non trovata con id {id}` (inesistente o non visibile), `Utente non trovato`;
  - 403 `Solo il creatore può invitare` (anche per l'admin);
  - 409 `La partita non è più aperta` (stato diverso da `creata` e `confermata`), `Gli inviti sono chiusi: la partita è già iniziata` (a inizio esatto sono già chiusi), `La partita è al completo`, `Non è possibile invitare un amministratore`, `L'utente partecipa già a questa partita`.

  Le regole sulla partita (creatore, stato, inizio, posti)
 sono quelle di `puoInvitare` (stesso codice nel service) e si controllano in quest'ordine, prima di quelle sull'invitato. La partita è letta con lo stesso lock in scrittura di `entra`, con il vincolo `uk_partecipazione_utente` come rete di sicurezza.
- `GET /api/partite/inviti` — bacheca inviti: le partite `creata` o `confermata` in cui l'utente ha un posto da invitato (`invitato=true`), ordinate per inizio effettivo crescente (a parità, id crescente). Per l'admin sempre `[]`. Risposta 200: `[ { id, idCampo, nomeCampo, dataPartita, oraInizio, oraFine, stato, tipo, nomeCreatore, cognomeCreatore } ]` (`stato` in minuscolo), `[]` se non ce ne sono. Partecipazione, partita, campo e creatore si leggono con una sola query (`JOIN FETCH`).

- `POST /api/partite/{id}/conferma` — senza body: un partecipante `UTENTE` conferma la propria partecipazione (`confermata=true`) tra 24 e 12 ore prima dell'inizio effettivo (finestra `[inizio−24h, inizio−12h)`: a esattamente 24h è aperta, a esattamente 12h è chiusa). Se dopo la conferma la partita ha 4 posti occupati e tutti i partecipanti `UTENTE` hanno confermato (i posti `CAMPO` non confermano), passa a `confermata`. Risposta 200 con il dettaglio di `GET /api/partite/{id}` aggiornato. Errori:
  - 404 `Partita non trovata con id {id}` se la partita non esiste o l'utente non la vede;
  - 403 `L'amministratore non conferma le partite`;
  - 409, in quest'ordine: `Non partecipi a questa partita`, `Hai già confermato la partecipazione`, `La partita non è più aperta` (stato diverso da `creata`), `La conferma è possibile solo tra 24 e 12 ore prima dell'inizio`.

  Le condizioni sono quelle di `puoConfermare` del dettaglio (stesso codice nel service). La partita è letta con lo stesso lock in scrittura di `entra`.

- `POST /api/partite/{id}/risultato` — un partecipante `UTENTE` registra il risultato tra la fine effettiva della partita e 24 ore dopo (finestra `[fine, fine+24h]`, estremi compresi). Body: `{ "squadra1": [11, 12], "squadra2": [13, 14], "set": [ { "squadra1": 6, "squadra2": 4 }, { "squadra1": 3, "squadra2": 6 }, { "squadra1": 6, "squadra2": 2 } ] }`, dove le squadre contengono gli `idPosto` dei 4 posti della partita, giocatori `CAMPO` compresi. Salva i game (`set3` resta NULL con 2 set), `telefono_registrante`, `data_risultato` e `squadra` sui 4 posti, e porta la partita a `giocata`. Il risultato vale subito, senza conferme degli altri giocatori. Nella stessa transazione assegna i punti (vedi sotto): se qualcosa fallisce non resta scritto nulla. Risposta 200 con il dettaglio di `GET /api/partite/{id}` aggiornato, con `risultato` valorizzato e `puntiAssegnati` uguale alla variazione applicata all'utente che registra. Errori, controllati in quest'ordine:
  - 403 `L'amministratore modifica i risultati dalla propria area`;
  - 404 `Partita non trovata con id {id}` se la partita non esiste o l'utente non la vede;
  - 409: `Non partecipi a questa partita` (vale anche per i posti `CAMPO`, che non registrano), `Il risultato è già stato registrato`, `Il risultato si può registrare solo per le partite confermate`, `La partita non è ancora finita`, `Il termine per registrare il risultato è scaduto`;
  - 400: `Ogni squadra deve avere 2 giocatori`, `Le squadre devono contenere i 4 posti della partita` (id ripetuti o non della partita), `Il set N non è valido: si vince a 6 game con almeno 2 di scarto, oppure 7-5 o 7-6` (punteggi validi, in entrambi i sensi: 6-0, 6-1, 6-2, 6-3, 6-4, 7-5, 7-6), `Il risultato deve avere 2 o 3 set e una squadra deve vincerne esattamente 2`, `Il terzo set non va inserito se una squadra ha vinto i primi due`. Il body non è validato dal controller: i controlli stanno nel service, dopo quelli sulla partita, quindi chi non vede la partita riceve 404 anche con un body sbagliato. Un JSON malformato, o un valore del tipo sbagliato, dà il 400 generico sul corpo della richiesta: vale anche per un numero con decimali in un campo intero, come un game `6.5` o `6.0`, che non viene troncato (`spring.jackson.deserialization.accept-float-as-int=false`, attivo su tutti gli endpoint).

  Le condizioni del 409 sono quelle di `puoRegistrareRisultato` del dettaglio (stesso codice nel service). La partita è letta con lo stesso lock in scrittura di `entra`: di due giocatori che registrano insieme, uno riceve 200 e l'altro 409 `Il risultato è già stato registrato`.

  Punti (`PunteggiService`): la media di ogni squadra usa il livello attuale dei giocatori e, per ogni posto `CAMPO`, `impostazioni.livello_giocatori_campo`. Ogni vincitore `UTENTE` riceve +3 se la media dei perdenti è maggiore o uguale a quella dei vincitori, altrimenti +1; ogni perdente `UTENTE` riceve -1. `utente.punteggio` non scende sotto 0 e `partecipazione.punti_assegnati` contiene la variazione effettivamente applicata (0 per un perdente a 0 punti). Per i posti `CAMPO` resta null. Le righe `utente` sono lette con un lock in scrittura, in ordine di telefono: partite con giocatori in comune registrate insieme si mettono in coda e nessuna variazione va persa. Le partite già `giocata` non vengono ricalcolate. `PunteggiService.stornaPunti` fa l'operazione inversa (minimo 0, `punti_assegnati` a null): lo usano la correzione del risultato e ogni uscita da `giocata` decisa dall'admin (vedi "Area amministratore").

### Classifica (richiede header `Authorization: Bearer <token>`)
- `GET /api/classifica` — gli utenti non admin, per punteggio decrescente e a parità per cognome e nome. Legge `utente.punteggio`, senza ricalcolare dallo storico. Risposta 200: `[ { posizione, telefono, nome, cognome, punteggio, utenteCorrente } ]`, `[]` se non ci sono utenti. Chi ha gli stessi punti ha la stessa `posizione` (1, 2, 2, 4). `utenteCorrente` è true solo sulla riga di chi chiama, quindi per un admin è sempre false. Nessun errore specifico (403 senza token).

### Area amministratore (richiede header `Authorization: Bearer <token>` di un admin)
Tutti gli endpoint `/api/admin/**` sono riservati al ruolo admin: un utente normale e chi non ha il token ricevono 403. Per il dettaglio di una partita l'admin usa `GET /api/partite/{id}`, che vede tutto con le azioni tutte false. Le risposte "dettaglio" hanno lo stesso formato, visto dall'admin (`azioni` tutte false; in `risultato` valgono null `squadraUtente`, `esito` e `puntiAssegnati`). Le scritture sulla partita prendono il lock in scrittura.

- `GET /api/admin/partite?data=2026-11-05` — le partite con quella `data_partita`, annullate comprese. Un elemento per ogni campo, anche senza partite, ordinati per nome; le partite sono ordinate per inizio effettivo, quindi quella delle 00:30 della serata viene dopo quella delle 18:00. Risposta 200: `[ { idCampo, nomeCampo, partite: [ { id, oraInizio, oraFine, stato, tipo, giocatori, postiLiberi, risultato } ] } ]`, con `stato` minuscolo, `giocatori` come `"Nome Cognome"` oppure `"Giocatore del campo"` e `risultato` come `[ { squadra1, squadra2 } ]` oppure null. Errore 400 se `data` manca o non è valida.
- `GET /api/admin/avvisi` — gli avvisi delle partite non annullate, non ancora iniziate e con meno di 4 posti occupati, ordinati per inizio effettivo. L'avviso di una partita completata resta salvato: se si riapre un posto torna visibile. Risposta 200: `[ { id, idPartita, nomeCampo, dataPartita, oraInizio, oraFine, postiLiberi } ]`.
- `GET /api/admin/utenti?cerca=<testo>` — gli utenti non admin, per cognome, nome e telefono. `cerca` è facoltativo e funziona come in `invitabili`. Risposta 200: `[ { telefono, nome, cognome, livello, punteggio } ]`.
- `PUT /api/admin/utenti/{telefono}/livello` — body `{ "livello": 2.5 }`. La riga dell'utente si rilegge con il lock in scrittura, così non si sovrascrive un punteggio appena aggiornato. Il cambio di livello non ricalcola i punti: quelli già assegnati e `punteggio` restano invariati. Risposta 200: `{ telefono, nome, cognome, livello, punteggio }`. Errori:
  - 400 `Il livello deve essere tra 0 e 5 con passo 0,5`;
  - 404 `Utente non trovato`, anche se il telefono è di un admin.
- `PUT /api/admin/partite/{id}/partecipanti` — body `{ "telefoni": ["3330000001", "3330000002"] }`, l'elenco completo dei partecipanti `UTENTE` dopo la modifica, senza limiti di tempo. I posti `CAMPO` restano e chi resta conserva i suoi dati. I nuovi hanno `invitato=false` e `confermata=true` solo se la partita è `confermata` o se mancano 12 ore o meno all'inizio effettivo (finestra di conferma chiusa). Una partita `creata` con 4 posti e tutti gli utenti confermati passa a `confermata`. Se l'elenco non contiene il creatore, il creatore della partita diventa l'admin del token (`telefonoCreatore` nella risposta): l'admin non diventa partecipante e non occupa un posto. Da quel momento nessun utente può invitare o eliminare la partita, i partecipanti possono uscire come sempre e una partita pubblica è visibile a tutti i livelli. Se poi l'admin riaggiunge l'ex creatore, questi è un partecipante normale e il creatore resta l'admin. Risposta 200: dettaglio. Errori, controllati in quest'ordine:
  - 400 `Almeno un giocatore deve essere un utente` (elenco vuoto o mancante);
  - 400 `L'elenco dei partecipanti contiene telefoni duplicati`;
  - 404 `Partita non trovata con id N`;
  - 409 `Si possono modificare solo le partite non annullate e non giocate`;
  - 400 `Una partita può avere al massimo 4 giocatori` (contando i posti `CAMPO`);
  - 404 `Utente non trovato`;
  - 409 `Un amministratore non può partecipare`.
- `PUT /api/admin/partite/{id}/risultato` — stesso body di `POST /api/partite/{id}/risultato`, senza limiti di tempo, per le partite `giocata` (correzione) e `confermata` (registrazione). In una sola transazione storna i punti già assegnati, salva set e squadre con `telefono_registrante` = admin e `data_risultato` = adesso, assegna i punti con i livelli attuali e porta la partita a `giocata`. Risposta 200: dettaglio. Errori, controllati in quest'ordine:
  - 404 `Partita non trovata con id N`;
  - 409 `Il risultato si può modificare solo per le partite confermate o giocate`;
  - 400 le stesse validazioni di squadre e set di `POST /api/partite/{id}/risultato`.
- `PUT /api/admin/partite/{id}/stato` — body `{ "stato": "annullata" }`, con `creata`, `confermata`, `annullata` o `giocata`. Qualunque transizione è ammessa. I punti esistono solo sulle partite `giocata`: a ogni uscita da `giocata`, verso qualunque stato, si stornano; a ogni ingresso in `giocata` con un risultato salvato si assegnano con i livelli attuali. Da `giocata` a `giocata` non cambia nulla. Il passaggio a `giocata` senza risultato resta permesso e non assegna punti; lo stesso vale se il risultato salvato non ha più due squadre complete, perché i partecipanti sono stati cambiati dopo l'uscita da `giocata` (il risultato si corregge con `PUT /api/admin/partite/{id}/risultato`). Il risultato resta salvato. Risposta 200: dettaglio. Errori:
  - 400 `Stato non valido`;
  - 404 `Partita non trovata con id N`;
  - 409 `Lo slot è già occupato da un'altra partita` (da `annullata` a un altro stato, se lo slot è stato preso nel frattempo).
- `POST /api/admin/partite/{id}/annulla` — senza body: porta la partita ad `annullata` in qualsiasi momento, con lo storno se era `giocata`. Il risultato resta salvato. Risposta 200: dettaglio. Errori:
  - 404 `Partita non trovata con id N`;
  - 409 `La partita è già annullata`.
- `GET /api/admin/impostazioni` — risposta 200: `{ livelloGiocatoriCampo }`.
- `PUT /api/admin/impostazioni/livello-giocatori-campo` — body `{ "livello": 2.5 }`. Vale per i calcoli successivi: i punti già assegnati non cambiano. Risposta 200: `{ livelloGiocatoriCampo }`. Errore 400 `Il livello deve essere tra 0 e 5 con passo 0,5`.
- `POST /api/admin/partite/{id}/giocatori-campo` — body `{ "numero": 2 }`: aggiunge posti `CAMPO`, senza limiti di tempo. Una partita `creata` con 4 posti e tutti gli utenti confermati passa a `confermata`, anche dopo le 12h prima dell'inizio. Risposta 200: dettaglio. Errori, controllati in quest'ordine:
  - 400 `Il numero deve essere tra 1 e 3`;
  - 404 `Partita non trovata con id N`;
  - 409 `Si possono modificare solo le partite non annullate e non giocate`;
  - 409 `Puoi aggiungere al massimo N giocatori del campo` (N = posti liberi; con N = 1 `Puoi aggiungere al massimo 1 giocatore del campo`);
  - 409 `Serve almeno un giocatore utente`.
- `DELETE /api/admin/partite/{id}/giocatori-campo/{idPosto}` — toglie un posto `CAMPO`. Lo stato non cambia: se una `confermata` resta sotto 4 posti, all'inizio la annulla lo scheduler. Risposta 200: dettaglio. Errori, controllati in quest'ordine:
  - 404 `Partita non trovata con id N`;
  - 409 `Si possono modificare solo le partite non annullate e non giocate`;
  - 404 `Giocatore del campo non trovato` (posto inesistente, di un'altra partita o di tipo `UTENTE`).
- `POST /api/admin/partite` — crea una partita da zero. Body: `{ "idCampo": 1, "dataPartita": "2026-11-05", "oraInizio": "18:00", "tipo": "PUBBLICA", "telefoni": ["3330000001"], "giocatoriCampo": 1 }`. Il creatore è l'admin del token e non occupa un posto, quindi valgono le regole del creatore admin (vedi `PUT /api/admin/partite/{id}/partecipanti`). `telefoni` ha da 1 a 4 utenti (`invitato=false`, `confermata=false`); `giocatoriCampo` è facoltativo (default 0); in tutto al massimo 4 posti. Lo stato è `creata`, anche con 4 posti, tranne a 12 ore o meno dall'inizio effettivo: lì gli utenti nascono con `confermata=true` e con 4 posti la partita nasce `confermata`. Le regole di slot sono quelle di `POST /api/prenotazioni`, compreso il limite di oggi + 15 giorni. Risposta 201: dettaglio. Errori, controllati in quest'ordine:
  - 400 `Il campo è obbligatorio`, `La data è obbligatoria`, `L'ora di inizio è obbligatoria`, `Il tipo è obbligatorio`, `Il tipo deve essere PUBBLICA o PRIVATA`;
  - 400 `Almeno un giocatore deve essere un utente`, `L'elenco dei partecipanti contiene telefoni duplicati`, `Il numero di giocatori del campo non può essere negativo`, `Una partita può avere al massimo 4 giocatori`;
  - 404 `Campo non trovato con id N`;
  - 409 `L'orario richiesto è fuori dalla fascia di apertura del campo`, `Non è possibile prenotare uno slot nel passato`, `Si può prenotare al massimo 15 giorni in anticipo`, `Lo slot selezionato è già stato prenotato`;
  - 404 `Utente non trovato`;

  - 409 `Un amministratore non può partecipare`.

## Attività pianificata delle scadenze

Ogni 60 secondi, e subito all'avvio, `ScadenzePartiteScheduler` esegue un giro di `ScadenzePartiteService`. Le condizioni sono "scadenza ≤ adesso" sull'inizio effettivo (anche dopo mezzanotte), non sul minuto esatto: dopo un fermo del server il primo giro applica le scadenze maturate nel frattempo, e ripetere un giro non cambia nulla.

- **−24h**: una partita `creata` con `inizio − 24h ≤ adesso < inizio` e meno di 4 posti occupati resta `creata` e riceve, una sola volta, un `avviso` `PARTITA_INCOMPLETA` (vincolo `uk_avviso`).
- **−12h**: nessuna modifica; la finestra di conferma si chiude e la partita resta `creata` se manca qualche conferma.
- **Inizio**: con `inizio ≤ adesso`, una partita `creata`, oppure `confermata` con meno di 4 posti, passa ad `annullata`. Le partite `giocata` non si toccano mai.

Il giro legge solo le partite `creata` e le `confermata` con meno di 4 posti con `data_partita` fino a dopodomani; ognuna si elabora in una transazione propria con il lock in scrittura, e un errore su una partita viene loggato senza fermare le altre. Ogni modifica produce un log INFO `Scadenze: partita {id}, passo {-24h|INIZIO}, esito {AVVISO_CREATO|ANNULLATA} (...)`; i giri senza modifiche si vedono con `logging.level.com.padelbooking.api.service.ScadenzePartiteService=DEBUG`.

Lo scheduler si disattiva con la proprietà `padel.scadenze.attive=false` (variabile d'ambiente `PADEL_SCADENZE_ATTIVE`, default `true`).

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
