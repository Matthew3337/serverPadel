package com.padelbooking.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.LocalTime;

public class PrenotazioneDTO {
    public static class CreateRequest {
        // Il creatore (giocatore 1) è l'utente autenticato e non viene più letto dal body:
        // i giocatori successivi entrano con inviti e bacheca pubblica.
        @NotNull(message = "Il campo è obbligatorio") private Integer idCampo;
        @NotNull(message = "La data è obbligatoria") private LocalDate dataPrenotazione;
        @NotNull(message = "L'ora di inizio è obbligatoria") private LocalTime oraInizio;
        // Stringa e non enum, così un valore errato dà 400 con messaggio invece di un errore di parsing.
        @NotBlank(message = "Il tipo è obbligatorio")
        @Pattern(regexp = "PUBBLICA|PRIVATA", message = "Il tipo deve essere PUBBLICA o PRIVATA") private String tipo;
        public CreateRequest() { }
        public Integer getIdCampo() { return idCampo; }
        public void setIdCampo(Integer idCampo) { this.idCampo = idCampo; }
        public LocalDate getDataPrenotazione() { return dataPrenotazione; }
        public void setDataPrenotazione(LocalDate value) { this.dataPrenotazione = value; }
        public LocalTime getOraInizio() { return oraInizio; }
        public void setOraInizio(LocalTime value) { this.oraInizio = value; }
        public String getTipo() { return tipo; }
        public void setTipo(String value) { this.tipo = value; }
    }

    public static class Response {
        private Integer id;
        private Integer idCampo;
        private String nomeCampo;
        private String telefonoGiocatore1;
        private String telefonoGiocatore2;
        private String telefonoGiocatore3;
        private String telefonoGiocatore4;
        private LocalDate dataPrenotazione;
        private LocalTime oraInizio;
        private LocalTime oraFine;
        private String stato;
        // PUBBLICA o PRIVATA
        private String tipo;
        public Response() { }
        public Response(Integer id, Integer idCampo, String nomeCampo, String telefonoGiocatore1,
                        String telefonoGiocatore2, String telefonoGiocatore3, String telefonoGiocatore4,
                        LocalDate dataPrenotazione, LocalTime oraInizio, LocalTime oraFine, String stato, String tipo) {
            this.id = id; this.idCampo = idCampo; this.nomeCampo = nomeCampo;
            this.telefonoGiocatore1 = telefonoGiocatore1; this.telefonoGiocatore2 = telefonoGiocatore2;
            this.telefonoGiocatore3 = telefonoGiocatore3; this.telefonoGiocatore4 = telefonoGiocatore4;
            this.dataPrenotazione = dataPrenotazione; this.oraInizio = oraInizio; this.oraFine = oraFine; this.stato = stato;
            this.tipo = tipo;
        }
        public Integer getId() { return id; }
        public void setId(Integer value) { this.id = value; }
        public Integer getIdCampo() { return idCampo; }
        public void setIdCampo(Integer value) { this.idCampo = value; }
        public String getNomeCampo() { return nomeCampo; }
        public void setNomeCampo(String value) { this.nomeCampo = value; }
        public String getTelefonoGiocatore1() { return telefonoGiocatore1; }
        public void setTelefonoGiocatore1(String value) { this.telefonoGiocatore1 = value; }
        public String getTelefonoGiocatore2() { return telefonoGiocatore2; }
        public void setTelefonoGiocatore2(String value) { this.telefonoGiocatore2 = value; }
        public String getTelefonoGiocatore3() { return telefonoGiocatore3; }
        public void setTelefonoGiocatore3(String value) { this.telefonoGiocatore3 = value; }
        public String getTelefonoGiocatore4() { return telefonoGiocatore4; }
        public void setTelefonoGiocatore4(String value) { this.telefonoGiocatore4 = value; }
        public LocalDate getDataPrenotazione() { return dataPrenotazione; }
        public void setDataPrenotazione(LocalDate value) { this.dataPrenotazione = value; }
        public LocalTime getOraInizio() { return oraInizio; }
        public void setOraInizio(LocalTime value) { this.oraInizio = value; }
        public LocalTime getOraFine() { return oraFine; }
        public void setOraFine(LocalTime value) { this.oraFine = value; }
        public String getStato() { return stato; }
        public void setStato(String value) { this.stato = value; }
        public String getTipo() { return tipo; }
        public void setTipo(String value) { this.tipo = value; }
    }

    public static class SlotResponse {
        private Integer idCampo;
        private String nomeCampo;
        private LocalTime oraInizio;
        private LocalTime oraFine;
        private Boolean disponibile;
        public SlotResponse() { }
        public SlotResponse(Integer idCampo, String nomeCampo, LocalTime oraInizio, LocalTime oraFine, Boolean disponibile) {
            this.idCampo = idCampo; this.nomeCampo = nomeCampo; this.oraInizio = oraInizio; this.oraFine = oraFine; this.disponibile = disponibile;
        }
        public Integer getIdCampo() { return idCampo; }
        public void setIdCampo(Integer value) { this.idCampo = value; }
        public String getNomeCampo() { return nomeCampo; }
        public void setNomeCampo(String value) { this.nomeCampo = value; }
        public LocalTime getOraInizio() { return oraInizio; }
        public void setOraInizio(LocalTime value) { this.oraInizio = value; }
        public LocalTime getOraFine() { return oraFine; }
        public void setOraFine(LocalTime value) { this.oraFine = value; }
        public Boolean getDisponibile() { return disponibile; }
        public void setDisponibile(Boolean value) { this.disponibile = value; }
    }
}
