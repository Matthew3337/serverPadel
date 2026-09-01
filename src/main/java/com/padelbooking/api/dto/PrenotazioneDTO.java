package com.padelbooking.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public class PrenotazioneDTO {

    public static class CreateRequest {
        @NotNull(message = "Il campo è obbligatorio")
        private Integer idCampo;

        // id del giocatore che effettua la prenotazione (preso comunque anche dal token JWT lato service)
        @NotNull(message = "Il giocatore 1 è obbligatorio")
        private Integer idGiocatore1;

        private Integer idGiocatore2;
        private Integer idGiocatore3;
        private Integer idGiocatore4;

        @NotNull(message = "La data è obbligatoria")
        private LocalDate dataPrenotazione;

        @NotNull(message = "L'ora di inizio è obbligatoria")
        private LocalTime oraInizio;

        public CreateRequest() {
        }

        public CreateRequest(Integer idCampo, Integer idGiocatore1, Integer idGiocatore2, Integer idGiocatore3,
                              Integer idGiocatore4, LocalDate dataPrenotazione, LocalTime oraInizio) {
            this.idCampo = idCampo;
            this.idGiocatore1 = idGiocatore1;
            this.idGiocatore2 = idGiocatore2;
            this.idGiocatore3 = idGiocatore3;
            this.idGiocatore4 = idGiocatore4;
            this.dataPrenotazione = dataPrenotazione;
            this.oraInizio = oraInizio;
        }

        public Integer getIdCampo() {
            return idCampo;
        }

        public void setIdCampo(Integer idCampo) {
            this.idCampo = idCampo;
        }

        public Integer getIdGiocatore1() {
            return idGiocatore1;
        }

        public void setIdGiocatore1(Integer idGiocatore1) {
            this.idGiocatore1 = idGiocatore1;
        }

        public Integer getIdGiocatore2() {
            return idGiocatore2;
        }

        public void setIdGiocatore2(Integer idGiocatore2) {
            this.idGiocatore2 = idGiocatore2;
        }

        public Integer getIdGiocatore3() {
            return idGiocatore3;
        }

        public void setIdGiocatore3(Integer idGiocatore3) {
            this.idGiocatore3 = idGiocatore3;
        }

        public Integer getIdGiocatore4() {
            return idGiocatore4;
        }

        public void setIdGiocatore4(Integer idGiocatore4) {
            this.idGiocatore4 = idGiocatore4;
        }

        public LocalDate getDataPrenotazione() {
            return dataPrenotazione;
        }

        public void setDataPrenotazione(LocalDate dataPrenotazione) {
            this.dataPrenotazione = dataPrenotazione;
        }

        public LocalTime getOraInizio() {
            return oraInizio;
        }

        public void setOraInizio(LocalTime oraInizio) {
            this.oraInizio = oraInizio;
        }
    }

    public static class Response {
        private Integer id;
        private Integer idCampo;
        private String nomeCampo;
        private Integer idGiocatore1;
        private Integer idGiocatore2;
        private Integer idGiocatore3;
        private Integer idGiocatore4;
        private LocalDate dataPrenotazione;
        private LocalTime oraInizio;
        private LocalTime oraFine;
        private String stato;

        public Response() {
        }

        public Response(Integer id, Integer idCampo, String nomeCampo, Integer idGiocatore1, Integer idGiocatore2,
                         Integer idGiocatore3, Integer idGiocatore4, LocalDate dataPrenotazione,
                         LocalTime oraInizio, LocalTime oraFine, String stato) {
            this.id = id;
            this.idCampo = idCampo;
            this.nomeCampo = nomeCampo;
            this.idGiocatore1 = idGiocatore1;
            this.idGiocatore2 = idGiocatore2;
            this.idGiocatore3 = idGiocatore3;
            this.idGiocatore4 = idGiocatore4;
            this.dataPrenotazione = dataPrenotazione;
            this.oraInizio = oraInizio;
            this.oraFine = oraFine;
            this.stato = stato;
        }

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public Integer getIdCampo() {
            return idCampo;
        }

        public void setIdCampo(Integer idCampo) {
            this.idCampo = idCampo;
        }

        public String getNomeCampo() {
            return nomeCampo;
        }

        public void setNomeCampo(String nomeCampo) {
            this.nomeCampo = nomeCampo;
        }

        public Integer getIdGiocatore1() {
            return idGiocatore1;
        }

        public void setIdGiocatore1(Integer idGiocatore1) {
            this.idGiocatore1 = idGiocatore1;
        }

        public Integer getIdGiocatore2() {
            return idGiocatore2;
        }

        public void setIdGiocatore2(Integer idGiocatore2) {
            this.idGiocatore2 = idGiocatore2;
        }

        public Integer getIdGiocatore3() {
            return idGiocatore3;
        }

        public void setIdGiocatore3(Integer idGiocatore3) {
            this.idGiocatore3 = idGiocatore3;
        }

        public Integer getIdGiocatore4() {
            return idGiocatore4;
        }

        public void setIdGiocatore4(Integer idGiocatore4) {
            this.idGiocatore4 = idGiocatore4;
        }

        public LocalDate getDataPrenotazione() {
            return dataPrenotazione;
        }

        public void setDataPrenotazione(LocalDate dataPrenotazione) {
            this.dataPrenotazione = dataPrenotazione;
        }

        public LocalTime getOraInizio() {
            return oraInizio;
        }

        public void setOraInizio(LocalTime oraInizio) {
            this.oraInizio = oraInizio;
        }

        public LocalTime getOraFine() {
            return oraFine;
        }

        public void setOraFine(LocalTime oraFine) {
            this.oraFine = oraFine;
        }

        public String getStato() {
            return stato;
        }

        public void setStato(String stato) {
            this.stato = stato;
        }
    }

    public static class SlotResponse {
        private LocalTime oraInizio;
        private LocalTime oraFine;
        private Boolean disponibile;

        public SlotResponse() {
        }

        public SlotResponse(LocalTime oraInizio, LocalTime oraFine, Boolean disponibile) {
            this.oraInizio = oraInizio;
            this.oraFine = oraFine;
            this.disponibile = disponibile;
        }

        public LocalTime getOraInizio() {
            return oraInizio;
        }

        public void setOraInizio(LocalTime oraInizio) {
            this.oraInizio = oraInizio;
        }

        public LocalTime getOraFine() {
            return oraFine;
        }

        public void setOraFine(LocalTime oraFine) {
            this.oraFine = oraFine;
        }

        public Boolean getDisponibile() {
            return disponibile;
        }

        public void setDisponibile(Boolean disponibile) {
            this.disponibile = disponibile;
        }
    }
}
