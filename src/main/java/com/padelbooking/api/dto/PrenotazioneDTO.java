package com.padelbooking.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

public class PrenotazioneDTO {
    public static class CreateRequest {
        @NotNull(message = "Il campo è obbligatorio") private Integer idCampo;
        @NotBlank(message = "Il telefono del giocatore 1 è obbligatorio") private String telefonoGiocatore1;
        private String telefonoGiocatore2;
        private String telefonoGiocatore3;
        private String telefonoGiocatore4;
        @NotNull(message = "La data è obbligatoria") private LocalDate dataPrenotazione;
        @NotNull(message = "L'ora di inizio è obbligatoria") private LocalTime oraInizio;
        public CreateRequest() { }
        public Integer getIdCampo() { return idCampo; }
        public void setIdCampo(Integer idCampo) { this.idCampo = idCampo; }
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
        public Response() { }
        public Response(Integer id, Integer idCampo, String nomeCampo, String telefonoGiocatore1,
                        String telefonoGiocatore2, String telefonoGiocatore3, String telefonoGiocatore4,
                        LocalDate dataPrenotazione, LocalTime oraInizio, LocalTime oraFine, String stato) {
            this.id = id; this.idCampo = idCampo; this.nomeCampo = nomeCampo;
            this.telefonoGiocatore1 = telefonoGiocatore1; this.telefonoGiocatore2 = telefonoGiocatore2;
            this.telefonoGiocatore3 = telefonoGiocatore3; this.telefonoGiocatore4 = telefonoGiocatore4;
            this.dataPrenotazione = dataPrenotazione; this.oraInizio = oraInizio; this.oraFine = oraFine; this.stato = stato;
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
    }

    public static class SlotResponse {
        private LocalTime oraInizio;
        private LocalTime oraFine;
        private Boolean disponibile;
        public SlotResponse() { }
        public SlotResponse(LocalTime oraInizio, LocalTime oraFine, Boolean disponibile) { this.oraInizio = oraInizio; this.oraFine = oraFine; this.disponibile = disponibile; }
        public LocalTime getOraInizio() { return oraInizio; }
        public void setOraInizio(LocalTime value) { this.oraInizio = value; }
        public LocalTime getOraFine() { return oraFine; }
        public void setOraFine(LocalTime value) { this.oraFine = value; }
        public Boolean getDisponibile() { return disponibile; }
        public void setDisponibile(Boolean value) { this.disponibile = value; }
    }
}
