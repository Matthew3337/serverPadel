package com.padelbooking.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;

public class AuthDTO {

    public static class RegisterRequest {
        @NotBlank(message = "Il telefono è obbligatorio")
        private String telefono;

        @NotBlank(message = "Il nome è obbligatorio !")
        private String nome;

        @NotBlank(message = "Il cognome è obbligatorio")
        private String cognome;

        @NotBlank(message = "La password è obbligatoria")
        private String password;

        @NotNull(message = "La data di nascita è obbligatoria")
        @Past(message = "La data di nascita deve essere nel passato")
        private LocalDate dataNascita;

        public RegisterRequest() {
        }

        public RegisterRequest(String telefono, String nome, String cognome, String password, LocalDate dataNascita) {
            this.telefono = telefono;
            this.nome = nome;
            this.cognome = cognome;
            this.password = password;
            this.dataNascita = dataNascita;
        }

        public String getTelefono() {
            return telefono;
        }

        public void setTelefono(String telefono) {
            this.telefono = telefono;
        }

        public String getNome() {
            return nome;
        }

        public void setNome(String nome) {
            this.nome = nome;
        }

        public String getCognome() {
            return cognome;
        }

        public void setCognome(String cognome) {
            this.cognome = cognome;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public LocalDate getDataNascita() {
            return dataNascita;
        }

        public void setDataNascita(LocalDate dataNascita) {
            this.dataNascita = dataNascita;
        }
    }

    public static class LoginRequest {
        @NotBlank(message = "Il telefono è obbligatorio")
        private String telefono;

        @NotBlank(message = "La password è obbligatoria")
        private String password;

        public LoginRequest() {
        }

        public LoginRequest(String telefono, String password) {
            this.telefono = telefono;
            this.password = password;
        }

        public String getTelefono() {
            return telefono;
        }

        public void setTelefono(String telefono) {
            this.telefono = telefono;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class AuthResponse {
        private Integer id;
        private String nome;
        private String cognome;
        private Boolean isAdmin;
        private Double livello;

        public AuthResponse() {
        }

        public AuthResponse(Integer id, String nome, String cognome, Boolean isAdmin, Double livello) {
            this.id = id;
            this.nome = nome;
            this.cognome = cognome;
            this.isAdmin = isAdmin;
            this.livello = livello;
        }

        public Double getLivello() {
            return livello;
        }

        public void setLivello(Double livello) {
            this.livello = livello;
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

        public String getCognome() {
            return cognome;
        }

        public void setCognome(String cognome) {
            this.cognome = cognome;
        }

        public Boolean getIsAdmin() {
            return isAdmin;
        }

        public void setIsAdmin(Boolean isAdmin) {
            this.isAdmin = isAdmin;
        }
    }

    public static class UtenteResponse {
        private Integer id;
        private String telefono;
        private String nome;
        private String cognome;
        private LocalDate dataNascita;
        private Boolean isAdmin;

        public UtenteResponse() {
        }

        public UtenteResponse(Integer id, String telefono, String nome, String cognome,
                               LocalDate dataNascita, Boolean isAdmin) {
            this.id = id;
            this.telefono = telefono;
            this.nome = nome;
            this.cognome = cognome;
            this.dataNascita = dataNascita;
            this.isAdmin = isAdmin;
        }

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public String getTelefono() {
            return telefono;
        }

        public void setTelefono(String telefono) {
            this.telefono = telefono;
        }

        public String getNome() {
            return nome;
        }

        public void setNome(String nome) {
            this.nome = nome;
        }

        public String getCognome() {
            return cognome;
        }

        public void setCognome(String cognome) {
            this.cognome = cognome;
        }

        public LocalDate getDataNascita() {
            return dataNascita;
        }

        public void setDataNascita(LocalDate dataNascita) {
            this.dataNascita = dataNascita;
        }

        public Boolean getIsAdmin() {
            return isAdmin;
        }

        public void setIsAdmin(Boolean isAdmin) {
            this.isAdmin = isAdmin;
        }
    }
}
