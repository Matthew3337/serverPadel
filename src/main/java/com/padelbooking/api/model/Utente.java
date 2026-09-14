package com.padelbooking.api.model;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "utente")
public class Utente {

    @Id
    @Column(nullable = false, length = 20)
    private String telefono;

    @Column(nullable = false, length = 50)
    private String nome;

    @Column(nullable = false, length = 50)
    private String cognome;

    // Hash della password (BCrypt), mai salvata in chiaro
    @Column(nullable = false)
    private String password;

    @Column(name = "data_nascita", nullable = false)
    private LocalDate dataNascita;

    @Column(name = "is_admin", nullable = false)
    private Boolean isAdmin = false;

    @Column(nullable = false)
    private Double livello = 0.0;

    public Utente() {
    }

    public Utente(String telefono, String nome, String cognome, String password,
                  LocalDate dataNascita, Boolean isAdmin) {
        this.telefono = telefono;
        this.nome = nome;
        this.cognome = cognome;
        this.password = password;
        this.dataNascita = dataNascita;
        this.isAdmin = isAdmin;
    }

    public Utente(String telefono, String nome, String cognome, String password,
                  LocalDate dataNascita, Boolean isAdmin, Double livello) {
        this.telefono = telefono;
        this.nome = nome;
        this.cognome = cognome;
        this.password = password;
        this.dataNascita = dataNascita;
        this.isAdmin = isAdmin;
        this.livello = livello;
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

    public Boolean getIsAdmin() {
        return isAdmin;
    }

    public void setIsAdmin(Boolean isAdmin) {
        this.isAdmin = isAdmin;
    }

    public Double getLivello() {
        return livello;
    }

    public void setLivello(Double livello) {
        this.livello = livello;
    }

}
