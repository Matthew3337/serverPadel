package com.padelbooking.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class PartitaDTO {

    // Dettaglio di una partita (UI_GP_3): dati dello slot, posti occupati e azioni disponibili
    // per l'utente che lo richiede in quel momento
    public static class DettaglioResponse {
        private Integer id;
        private Integer idCampo;
        private String nomeCampo;
        private LocalDate dataPartita;
        private LocalTime oraInizio;
        private LocalTime oraFine;
        // In minuscolo come in PrenotazioneDTO: creata, confermata, annullata, giocata
        private String stato;
        // PUBBLICA o PRIVATA
        private String tipo;
        private String telefonoCreatore;
        // Solo i posti occupati, con il creatore per primo
        private List<PostoResponse> posti;
        private AzioniResponse azioni;
        // null finché il risultato non è registrato; visto dall'utente del token
        private RisultatoResponse risultato;
        public DettaglioResponse() { }
        public DettaglioResponse(Integer id, Integer idCampo, String nomeCampo, LocalDate dataPartita,
                                 LocalTime oraInizio, LocalTime oraFine, String stato, String tipo,
                                 String telefonoCreatore, List<PostoResponse> posti, AzioniResponse azioni,
                                 RisultatoResponse risultato) {
            this.id = id; this.idCampo = idCampo; this.nomeCampo = nomeCampo; this.dataPartita = dataPartita;
            this.oraInizio = oraInizio; this.oraFine = oraFine; this.stato = stato; this.tipo = tipo;
            this.telefonoCreatore = telefonoCreatore; this.posti = posti; this.azioni = azioni;
            this.risultato = risultato;
        }
        public Integer getId() { return id; }
        public void setId(Integer value) { this.id = value; }
        public Integer getIdCampo() { return idCampo; }
        public void setIdCampo(Integer value) { this.idCampo = value; }
        public String getNomeCampo() { return nomeCampo; }
        public void setNomeCampo(String value) { this.nomeCampo = value; }
        public LocalDate getDataPartita() { return dataPartita; }
        public void setDataPartita(LocalDate value) { this.dataPartita = value; }
        public LocalTime getOraInizio() { return oraInizio; }
        public void setOraInizio(LocalTime value) { this.oraInizio = value; }
        public LocalTime getOraFine() { return oraFine; }
        public void setOraFine(LocalTime value) { this.oraFine = value; }
        public String getStato() { return stato; }
        public void setStato(String value) { this.stato = value; }
        public String getTipo() { return tipo; }
        public void setTipo(String value) { this.tipo = value; }
        public String getTelefonoCreatore() { return telefonoCreatore; }
        public void setTelefonoCreatore(String value) { this.telefonoCreatore = value; }
        public List<PostoResponse> getPosti() { return posti; }
        public void setPosti(List<PostoResponse> value) { this.posti = value; }
        public AzioniResponse getAzioni() { return azioni; }
        public void setAzioni(AzioniResponse value) { this.azioni = value; }
        public RisultatoResponse getRisultato() { return risultato; }
        public void setRisultato(RisultatoResponse value) { this.risultato = value; }
    }

    // Un posto occupato: per i giocatori CAMPO (messi dal circolo) i dati dell'utente sono null
    public static class PostoResponse {
        // Id della partecipazione: è il valore da mandare nelle squadre di POST /api/partite/{id}/risultato
        private Integer idPosto;
        // UTENTE o CAMPO
        private String tipo;
        private String telefono;
        private String nome;
        private String cognome;
        private Double livello;
        private Boolean confermata;
        // 1 o 2; null finché il risultato non è registrato
        private Integer squadra;
        public PostoResponse() { }
        public PostoResponse(Integer idPosto, String tipo, String telefono, String nome, String cognome, Double livello,
                             Boolean confermata, Integer squadra) {
            this.idPosto = idPosto; this.tipo = tipo; this.telefono = telefono; this.nome = nome;
            this.cognome = cognome; this.livello = livello; this.confermata = confermata; this.squadra = squadra;
        }
        public Integer getIdPosto() { return idPosto; }
        public void setIdPosto(Integer value) { this.idPosto = value; }
        public String getTipo() { return tipo; }
        public void setTipo(String value) { this.tipo = value; }
        public String getTelefono() { return telefono; }
        public void setTelefono(String value) { this.telefono = value; }
        public String getNome() { return nome; }
        public void setNome(String value) { this.nome = value; }
        public String getCognome() { return cognome; }
        public void setCognome(String value) { this.cognome = value; }
        public Double getLivello() { return livello; }
        public void setLivello(Double value) { this.livello = value; }
        public Boolean getConfermata() { return confermata; }
        public void setConfermata(Boolean value) { this.confermata = value; }
        public Integer getSquadra() { return squadra; }
        public void setSquadra(Integer value) { this.squadra = value; }
    }

    // Azioni che l'utente del token può fare adesso sulla partita: il client mostra solo i pulsanti a true
    public static class AzioniResponse {
        private Boolean puoInvitare;
        private Boolean puoEliminare;
        private Boolean puoUscire;
        private Boolean puoConfermare;
        private Boolean puoRegistrareRisultato;
        public AzioniResponse() { }
        public AzioniResponse(Boolean puoInvitare, Boolean puoEliminare, Boolean puoUscire,
                              Boolean puoConfermare, Boolean puoRegistrareRisultato) {
            this.puoInvitare = puoInvitare; this.puoEliminare = puoEliminare; this.puoUscire = puoUscire;
            this.puoConfermare = puoConfermare; this.puoRegistrareRisultato = puoRegistrareRisultato;
        }
        public Boolean getPuoInvitare() { return puoInvitare; }
        public void setPuoInvitare(Boolean value) { this.puoInvitare = value; }
        public Boolean getPuoEliminare() { return puoEliminare; }
        public void setPuoEliminare(Boolean value) { this.puoEliminare = value; }
        public Boolean getPuoUscire() { return puoUscire; }
        public void setPuoUscire(Boolean value) { this.puoUscire = value; }
        public Boolean getPuoConfermare() { return puoConfermare; }
        public void setPuoConfermare(Boolean value) { this.puoConfermare = value; }
        public Boolean getPuoRegistrareRisultato() { return puoRegistrareRisultato; }
        public void setPuoRegistrareRisultato(Boolean value) { this.puoRegistrareRisultato = value; }
    }

    // Una riga della bacheca delle partite pubbliche (UI_GP_4): lo slot, chi l'ha creata e quanti posti restano
    public static class BachecaResponse {
        private Integer id;
        private Integer idCampo;
        private String nomeCampo;
        private LocalDate dataPartita;
        private LocalTime oraInizio;
        private LocalTime oraFine;
        private String nomeCreatore;
        private String cognomeCreatore;
        private Double livelloCreatore;
        // 4 meno i posti occupati (UTENTE o CAMPO)
        private Integer postiLiberi;
        public BachecaResponse() { }
        public BachecaResponse(Integer id, Integer idCampo, String nomeCampo, LocalDate dataPartita,
                               LocalTime oraInizio, LocalTime oraFine, String nomeCreatore, String cognomeCreatore,
                               Double livelloCreatore, Integer postiLiberi) {
            this.id = id; this.idCampo = idCampo; this.nomeCampo = nomeCampo; this.dataPartita = dataPartita;
            this.oraInizio = oraInizio; this.oraFine = oraFine; this.nomeCreatore = nomeCreatore;
            this.cognomeCreatore = cognomeCreatore; this.livelloCreatore = livelloCreatore; this.postiLiberi = postiLiberi;
        }
        public Integer getId() { return id; }
        public void setId(Integer value) { this.id = value; }
        public Integer getIdCampo() { return idCampo; }
        public void setIdCampo(Integer value) { this.idCampo = value; }
        public String getNomeCampo() { return nomeCampo; }
        public void setNomeCampo(String value) { this.nomeCampo = value; }
        public LocalDate getDataPartita() { return dataPartita; }
        public void setDataPartita(LocalDate value) { this.dataPartita = value; }
        public LocalTime getOraInizio() { return oraInizio; }
        public void setOraInizio(LocalTime value) { this.oraInizio = value; }
        public LocalTime getOraFine() { return oraFine; }
        public void setOraFine(LocalTime value) { this.oraFine = value; }
        public String getNomeCreatore() { return nomeCreatore; }
        public void setNomeCreatore(String value) { this.nomeCreatore = value; }
        public String getCognomeCreatore() { return cognomeCreatore; }
        public void setCognomeCreatore(String value) { this.cognomeCreatore = value; }
        public Double getLivelloCreatore() { return livelloCreatore; }
        public void setLivelloCreatore(Double value) { this.livelloCreatore = value; }
        public Integer getPostiLiberi() { return postiLiberi; }
        public void setPostiLiberi(Integer value) { this.postiLiberi = value; }
    }

    // Una riga dello storico (UI_GP_8): lo slot, lo stato e, se registrato, il risultato visto dall'utente
    public static class StoricoResponse {
        private Integer id;
        private Integer idCampo;
        private String nomeCampo;
        private LocalDate dataPartita;
        private LocalTime oraInizio;
        private LocalTime oraFine;
        // In minuscolo come in DettaglioResponse: creata, confermata, giocata (le annullate non ci sono)
        private String stato;
        // PUBBLICA o PRIVATA
        private String tipo;
        // null finché il risultato non è registrato
        private RisultatoResponse risultato;
        public StoricoResponse() { }
        public StoricoResponse(Integer id, Integer idCampo, String nomeCampo, LocalDate dataPartita,
                               LocalTime oraInizio, LocalTime oraFine, String stato, String tipo,
                               RisultatoResponse risultato) {
            this.id = id; this.idCampo = idCampo; this.nomeCampo = nomeCampo; this.dataPartita = dataPartita;
            this.oraInizio = oraInizio; this.oraFine = oraFine; this.stato = stato; this.tipo = tipo;
            this.risultato = risultato;
        }
        public Integer getId() { return id; }
        public void setId(Integer value) { this.id = value; }
        public Integer getIdCampo() { return idCampo; }
        public void setIdCampo(Integer value) { this.idCampo = value; }
        public String getNomeCampo() { return nomeCampo; }
        public void setNomeCampo(String value) { this.nomeCampo = value; }
        public LocalDate getDataPartita() { return dataPartita; }
        public void setDataPartita(LocalDate value) { this.dataPartita = value; }
        public LocalTime getOraInizio() { return oraInizio; }
        public void setOraInizio(LocalTime value) { this.oraInizio = value; }
        public LocalTime getOraFine() { return oraFine; }
        public void setOraFine(LocalTime value) { this.oraFine = value; }
        public String getStato() { return stato; }
        public void setStato(String value) { this.stato = value; }
        public String getTipo() { return tipo; }
        public void setTipo(String value) { this.tipo = value; }
        public RisultatoResponse getRisultato() { return risultato; }
        public void setRisultato(RisultatoResponse value) { this.risultato = value; }
    }

    // Risultato dal punto di vista dell'utente: squadraUtente ed esito sono null se l'utente non ha
    // una squadra (anche l'admin e chi non gioca), puntiAssegnati è null finché i punteggi non sono calcolati (T10)
    public static class RisultatoResponse {
        // Solo i set giocati: 2 o 3
        private List<SetResponse> set;
        // 1 o 2
        private Integer squadraUtente;
        // VINTA o PERSA
        private String esito;
        private Integer puntiAssegnati;
        public RisultatoResponse() { }
        public RisultatoResponse(List<SetResponse> set, Integer squadraUtente, String esito, Integer puntiAssegnati) {
            this.set = set; this.squadraUtente = squadraUtente; this.esito = esito; this.puntiAssegnati = puntiAssegnati;
        }
        public List<SetResponse> getSet() { return set; }
        public void setSet(List<SetResponse> value) { this.set = value; }
        public Integer getSquadraUtente() { return squadraUtente; }
        public void setSquadraUtente(Integer value) { this.squadraUtente = value; }
        public String getEsito() { return esito; }
        public void setEsito(String value) { this.esito = value; }
        public Integer getPuntiAssegnati() { return puntiAssegnati; }
        public void setPuntiAssegnati(Integer value) { this.puntiAssegnati = value; }
    }

    // I game fatti da ciascuna squadra in un set
    public static class SetResponse {
        private Integer squadra1;
        private Integer squadra2;
        public SetResponse() { }
        public SetResponse(Integer squadra1, Integer squadra2) {
            this.squadra1 = squadra1; this.squadra2 = squadra2;
        }
        public Integer getSquadra1() { return squadra1; }
        public void setSquadra1(Integer value) { this.squadra1 = value; }
        public Integer getSquadra2() { return squadra2; }
        public void setSquadra2(Integer value) { this.squadra2 = value; }
    }

    // Body di POST /api/partite/{id}/risultato. Niente annotazioni di validazione: i messaggi sono quelli
    // del contratto, senza il prefisso del campo, e il controllo sta nel service dopo quelli sulla
    // partita, perché le squadre si confrontano con i suoi posti e chi non la vede deve ricevere 404
    public static class RisultatoRequest {
        // Gli idPosto dei giocatori di ciascuna squadra, giocatori campo compresi
        private List<Integer> squadra1;
        private List<Integer> squadra2;
        private List<SetRequest> set;
        public RisultatoRequest() { }
        public RisultatoRequest(List<Integer> squadra1, List<Integer> squadra2, List<SetRequest> set) {
            this.squadra1 = squadra1; this.squadra2 = squadra2; this.set = set;
        }
        public List<Integer> getSquadra1() { return squadra1; }
        public void setSquadra1(List<Integer> value) { this.squadra1 = value; }
        public List<Integer> getSquadra2() { return squadra2; }
        public void setSquadra2(List<Integer> value) { this.squadra2 = value; }
        public List<SetRequest> getSet() { return set; }
        public void setSet(List<SetRequest> value) { this.set = value; }
    }

    // I game fatti da ciascuna squadra in un set della richiesta
    public static class SetRequest {
        private Integer squadra1;
        private Integer squadra2;
        public SetRequest() { }
        public SetRequest(Integer squadra1, Integer squadra2) {
            this.squadra1 = squadra1; this.squadra2 = squadra2;
        }
        public Integer getSquadra1() { return squadra1; }
        public void setSquadra1(Integer value) { this.squadra1 = value; }
        public Integer getSquadra2() { return squadra2; }
        public void setSquadra2(Integer value) { this.squadra2 = value; }
    }

    // Body di POST /api/partite/{id}/inviti: l'utente da aggiungere alla partita
    public static class InvitoRequest {
        @NotBlank(message = "Il telefono è obbligatorio")
        private String telefono;
        public InvitoRequest() { }
        public InvitoRequest(String telefono) { this.telefono = telefono; }
        public String getTelefono() { return telefono; }
        public void setTelefono(String value) { this.telefono = value; }
    }

    // Un utente che il creatore può invitare (UI_GI_2_SelezioneInvitati)
    public static class InvitabileResponse {
        private String telefono;
        private String nome;
        private String cognome;
        private Double livello;
        public InvitabileResponse() { }
        public InvitabileResponse(String telefono, String nome, String cognome, Double livello) {
            this.telefono = telefono; this.nome = nome; this.cognome = cognome; this.livello = livello;
        }
        public String getTelefono() { return telefono; }
        public void setTelefono(String value) { this.telefono = value; }
        public String getNome() { return nome; }
        public void setNome(String value) { this.nome = value; }
        public String getCognome() { return cognome; }
        public void setCognome(String value) { this.cognome = value; }
        public Double getLivello() { return livello; }
        public void setLivello(Double value) { this.livello = value; }
    }

    // Una riga della bacheca inviti (UC_GI_2): lo slot, lo stato e chi ha invitato l'utente
    public static class InvitoRicevutoResponse {
        private Integer id;
        private Integer idCampo;
        private String nomeCampo;
        private LocalDate dataPartita;
        private LocalTime oraInizio;
        private LocalTime oraFine;
        // In minuscolo come in DettaglioResponse: creata o confermata
        private String stato;
        // PUBBLICA o PRIVATA
        private String tipo;
        private String nomeCreatore;
        private String cognomeCreatore;
        public InvitoRicevutoResponse() { }
        public InvitoRicevutoResponse(Integer id, Integer idCampo, String nomeCampo, LocalDate dataPartita,
                                      LocalTime oraInizio, LocalTime oraFine, String stato, String tipo,
                                      String nomeCreatore, String cognomeCreatore) {
            this.id = id; this.idCampo = idCampo; this.nomeCampo = nomeCampo; this.dataPartita = dataPartita;
            this.oraInizio = oraInizio; this.oraFine = oraFine; this.stato = stato; this.tipo = tipo;
            this.nomeCreatore = nomeCreatore; this.cognomeCreatore = cognomeCreatore;
        }
        public Integer getId() { return id; }
        public void setId(Integer value) { this.id = value; }
        public Integer getIdCampo() { return idCampo; }
        public void setIdCampo(Integer value) { this.idCampo = value; }
        public String getNomeCampo() { return nomeCampo; }
        public void setNomeCampo(String value) { this.nomeCampo = value; }
        public LocalDate getDataPartita() { return dataPartita; }
        public void setDataPartita(LocalDate value) { this.dataPartita = value; }
        public LocalTime getOraInizio() { return oraInizio; }
        public void setOraInizio(LocalTime value) { this.oraInizio = value; }
        public LocalTime getOraFine() { return oraFine; }
        public void setOraFine(LocalTime value) { this.oraFine = value; }
        public String getStato() { return stato; }
        public void setStato(String value) { this.stato = value; }
        public String getTipo() { return tipo; }
        public void setTipo(String value) { this.tipo = value; }
        public String getNomeCreatore() { return nomeCreatore; }
        public void setNomeCreatore(String value) { this.nomeCreatore = value; }
        public String getCognomeCreatore() { return cognomeCreatore; }
        public void setCognomeCreatore(String value) { this.cognomeCreatore = value; }
    }
}
