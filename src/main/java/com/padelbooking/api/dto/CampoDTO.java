package com.padelbooking.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public class CampoDTO {

    public static class Request {
        @NotBlank(message = "Il nome del campo è obbligatorio")
        private String nome;

        @NotNull(message = "Specificare se il campo è al coperto")
        private Boolean alCoperto;

        @NotNull(message = "L'orario di apertura è obbligatorio")
        private LocalTime oraApertura;

        @NotNull(message = "L'orario di chiusura è obbligatorio")
        private LocalTime oraChiusura;

        public Request() {
        }

        public Request(String nome, Boolean alCoperto, LocalTime oraApertura, LocalTime oraChiusura) {
            this.nome = nome;
            this.alCoperto = alCoperto;
            this.oraApertura = oraApertura;
            this.oraChiusura = oraChiusura;
        }

        public String getNome() {
            return nome;
        }

        public void setNome(String nome) {
            this.nome = nome;
        }

        public Boolean getAlCoperto() {
            return alCoperto;
        }

        public void setAlCoperto(Boolean alCoperto) {
            this.alCoperto = alCoperto;
        }

        public LocalTime getOraApertura() {
            return oraApertura;
        }

        public void setOraApertura(LocalTime oraApertura) {
            this.oraApertura = oraApertura;
        }

        public LocalTime getOraChiusura() {
            return oraChiusura;
        }

        public void setOraChiusura(LocalTime oraChiusura) {
            this.oraChiusura = oraChiusura;
        }
    }

    public static class Response {
        private Integer id;
        private String nome;
        private Boolean alCoperto;
        private LocalTime oraApertura;
        private LocalTime oraChiusura;

        public Response() {
        }

        public Response(Integer id, String nome, Boolean alCoperto, LocalTime oraApertura, LocalTime oraChiusura) {
            this.id = id;
            this.nome = nome;
            this.alCoperto = alCoperto;
            this.oraApertura = oraApertura;
            this.oraChiusura = oraChiusura;
        }

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public String getNome() {
            return nome;
        }

        public void setNome(String nome) {
            this.nome = nome;
        }

        public Boolean getAlCoperto() {
            return alCoperto;
        }

        public void setAlCoperto(Boolean alCoperto) {
            this.alCoperto = alCoperto;
        }

        public LocalTime getOraApertura() {
            return oraApertura;
        }

        public void setOraApertura(LocalTime oraApertura) {
            this.oraApertura = oraApertura;
        }

        public LocalTime getOraChiusura() {
            return oraChiusura;
        }

        public void setOraChiusura(LocalTime oraChiusura) {
            this.oraChiusura = oraChiusura;
        }
    }
}
