package com.padelbooking.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

// Richieste e risposte dell'area amministratore. Le risposte con il dettaglio di una partita riusano
// PartitaDTO.DettaglioResponse e la modifica del risultato riusa PartitaDTO.RisultatoRequest.
// Niente annotazioni di validazione sulle richieste: i messaggi sono quelli del contratto, senza il
// prefisso del campo, e il controllo sta nel service, come per PartitaDTO.RisultatoRequest.
public class AdminDTO {

    // Le partite di un giorno su un campo, anche nessuna (UI_GA_2)
    public static class CampoPartiteResponse {
        private Integer idCampo;
        private String nomeCampo;
        private List<PartitaResponse> partite;
        public CampoPartiteResponse() { }
        public CampoPartiteResponse(Integer idCampo, String nomeCampo, List<PartitaResponse> partite) {
            this.idCampo = idCampo; this.nomeCampo = nomeCampo; this.partite = partite;
        }
        public Integer getIdCampo() { return idCampo; }
        public void setIdCampo(Integer value) { this.idCampo = value; }
        public String getNomeCampo() { return nomeCampo; }
        public void setNomeCampo(String value) { this.nomeCampo = value; }
        public List<PartitaResponse> getPartite() { return partite; }
        public void setPartite(List<PartitaResponse> value) { this.partite = value; }
    }

    // Una partita nell'elenco del giorno, annullate comprese
    public static class PartitaResponse {
        private Integer id;
        private LocalTime oraInizio;
        private LocalTime oraFine;
        private String stato;
        private String tipo;
        // "Nome Cognome" dei posti UTENTE, "Giocatore del campo" per i posti CAMPO
        private List<String> giocatori;
        private Integer postiLiberi;
        // I set giocati, null finché il risultato non è registrato
        private List<PartitaDTO.SetResponse> risultato;
        public PartitaResponse() { }
        public PartitaResponse(Integer id, LocalTime oraInizio, LocalTime oraFine, String stato, String tipo,
                               List<String> giocatori, Integer postiLiberi, List<PartitaDTO.SetResponse> risultato) {
            this.id = id; this.oraInizio = oraInizio; this.oraFine = oraFine; this.stato = stato; this.tipo = tipo;
            this.giocatori = giocatori; this.postiLiberi = postiLiberi; this.risultato = risultato;
        }
        public Integer getId() { return id; }
        public void setId(Integer value) { this.id = value; }
        public LocalTime getOraInizio() { return oraInizio; }
        public void setOraInizio(LocalTime value) { this.oraInizio = value; }
        public LocalTime getOraFine() { return oraFine; }
        public void setOraFine(LocalTime value) { this.oraFine = value; }
        public String getStato() { return stato; }
        public void setStato(String value) { this.stato = value; }
        public String getTipo() { return tipo; }
        public void setTipo(String value) { this.tipo = value; }
        public List<String> getGiocatori() { return giocatori; }
        public void setGiocatori(List<String> value) { this.giocatori = value; }
        public Integer getPostiLiberi() { return postiLiberi; }
        public void setPostiLiberi(Integer value) { this.postiLiberi = value; }
        public List<PartitaDTO.SetResponse> getRisultato() { return risultato; }
        public void setRisultato(List<PartitaDTO.SetResponse> value) { this.risultato = value; }
    }

    // Un avviso aperto nella bacheca dell'amministratore (UI_GA_6)
    public static class AvvisoResponse {
        private Integer id;
        private Integer idPartita;
        private String nomeCampo;
        private LocalDate dataPartita;
        private LocalTime oraInizio;
        private LocalTime oraFine;
        private Integer postiLiberi;
        public AvvisoResponse() { }
        public AvvisoResponse(Integer id, Integer idPartita, String nomeCampo, LocalDate dataPartita,
                              LocalTime oraInizio, LocalTime oraFine, Integer postiLiberi) {
            this.id = id; this.idPartita = idPartita; this.nomeCampo = nomeCampo; this.dataPartita = dataPartita;
            this.oraInizio = oraInizio; this.oraFine = oraFine; this.postiLiberi = postiLiberi;
        }
        public Integer getId() { return id; }
        public void setId(Integer value) { this.id = value; }
        public Integer getIdPartita() { return idPartita; }
        public void setIdPartita(Integer value) { this.idPartita = value; }
        public String getNomeCampo() { return nomeCampo; }
        public void setNomeCampo(String value) { this.nomeCampo = value; }
        public LocalDate getDataPartita() { return dataPartita; }
        public void setDataPartita(LocalDate value) { this.dataPartita = value; }
        public LocalTime getOraInizio() { return oraInizio; }
        public void setOraInizio(LocalTime value) { this.oraInizio = value; }
        public LocalTime getOraFine() { return oraFine; }
        public void setOraFine(LocalTime value) { this.oraFine = value; }
        public Integer getPostiLiberi() { return postiLiberi; }
        public void setPostiLiberi(Integer value) { this.postiLiberi = value; }
    }

    // Un utente nella gestione dei livelli (UI_GU_3)
    public static class UtenteResponse {
        private String telefono;
        private String nome;
        private String cognome;
        private Double livello;
        private Integer punteggio;
        public UtenteResponse() { }
        public UtenteResponse(String telefono, String nome, String cognome, Double livello, Integer punteggio) {
            this.telefono = telefono; this.nome = nome; this.cognome = cognome;
            this.livello = livello; this.punteggio = punteggio;
        }
        public String getTelefono() { return telefono; }
        public void setTelefono(String value) { this.telefono = value; }
        public String getNome() { return nome; }
        public void setNome(String value) { this.nome = value; }
        public String getCognome() { return cognome; }
        public void setCognome(String value) { this.cognome = value; }
        public Double getLivello() { return livello; }
        public void setLivello(Double value) { this.livello = value; }
        public Integer getPunteggio() { return punteggio; }
        public void setPunteggio(Integer value) { this.punteggio = value; }
    }

    // Body di PUT /api/admin/utenti/{telefono}/livello
    public static class LivelloRequest {
        private Double livello;
        public LivelloRequest() { }
        public LivelloRequest(Double livello) { this.livello = livello; }
        public Double getLivello() { return livello; }
        public void setLivello(Double value) { this.livello = value; }
    }

    // Body di PUT /api/admin/partite/{id}/partecipanti: l'elenco completo dei partecipanti UTENTE dopo la modifica
    public static class PartecipantiRequest {
        private List<String> telefoni;
        public PartecipantiRequest() { }
        public PartecipantiRequest(List<String> telefoni) { this.telefoni = telefoni; }
        public List<String> getTelefoni() { return telefoni; }
        public void setTelefoni(List<String> value) { this.telefoni = value; }
    }

    // Risposta di GET /api/admin/impostazioni e PUT /api/admin/impostazioni/livello-giocatori-campo.
    // Il body del PUT è LivelloRequest
    public static class ImpostazioniResponse {
        private Double livelloGiocatoriCampo;
        public ImpostazioniResponse() { }
        public ImpostazioniResponse(Double livelloGiocatoriCampo) { this.livelloGiocatoriCampo = livelloGiocatoriCampo; }
        public Double getLivelloGiocatoriCampo() { return livelloGiocatoriCampo; }
        public void setLivelloGiocatoriCampo(Double value) { this.livelloGiocatoriCampo = value; }
    }

    // Body di POST /api/admin/partite/{id}/giocatori-campo
    public static class GiocatoriCampoRequest {
        private Integer numero;
        public GiocatoriCampoRequest() { }
        public GiocatoriCampoRequest(Integer numero) { this.numero = numero; }
        public Integer getNumero() { return numero; }
        public void setNumero(Integer value) { this.numero = value; }
    }

    // Body di POST /api/admin/partite. Il creatore è l'admin del token e non occupa un posto
    public static class NuovaPartitaRequest {
        private Integer idCampo;
        private LocalDate dataPartita;
        private LocalTime oraInizio;
        // Stringa e non enum, così un valore errato dà 400 con messaggio, come in PrenotazioneDTO.CreateRequest
        private String tipo;
        private List<String> telefoni;
        // Facoltativo: se manca vale 0
        private Integer giocatoriCampo;
        public NuovaPartitaRequest() { }
        public Integer getIdCampo() { return idCampo; }
        public void setIdCampo(Integer value) { this.idCampo = value; }
        public LocalDate getDataPartita() { return dataPartita; }
        public void setDataPartita(LocalDate value) { this.dataPartita = value; }
        public LocalTime getOraInizio() { return oraInizio; }
        public void setOraInizio(LocalTime value) { this.oraInizio = value; }
        public String getTipo() { return tipo; }
        public void setTipo(String value) { this.tipo = value; }
        public List<String> getTelefoni() { return telefoni; }
        public void setTelefoni(List<String> value) { this.telefoni = value; }
        public Integer getGiocatoriCampo() { return giocatoriCampo; }
        public void setGiocatoriCampo(Integer value) { this.giocatoriCampo = value; }
    }

    // Body di PUT /api/admin/partite/{id}/stato: creata, confermata, annullata o giocata
    public static class StatoRequest {
        private String stato;
        public StatoRequest() { }
        public StatoRequest(String stato) { this.stato = stato; }
        public String getStato() { return stato; }
        public void setStato(String value) { this.stato = value; }
    }
}
