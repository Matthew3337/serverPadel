package com.padelbooking.api.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// Un posto occupato in una partita. Le righe di tipo CAMPO (giocatore del circolo) non hanno utente.
@Entity
@Table(name = "partecipazione")
public class Partecipazione {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_partita", nullable = false)
    private Partita partita;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(nullable = false)
    private TipoPartecipazione tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "telefono_utente", referencedColumnName = "telefono")
    private Utente utente;

    // 1 o 2, valorizzata solo quando si registra il risultato
    @Column(columnDefinition = "TINYINT")
    private Integer squadra;

    @Column(nullable = false)
    private Boolean invitato = false;

    @Column(nullable = false)
    private Boolean confermata = false;

    // Punti dati al giocatore con il risultato, conservati per poterli stornare
    @Column(name = "punti_assegnati")
    private Integer puntiAssegnati;

    public Partecipazione() {
    }

    public Partecipazione(TipoPartecipazione tipo, Utente utente, Boolean invitato, Boolean confermata) {
        this.tipo = tipo;
        this.utente = utente;
        this.invitato = invitato;
        this.confermata = confermata;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Partita getPartita() {
        return partita;
    }

    public void setPartita(Partita partita) {
        this.partita = partita;
    }

    public TipoPartecipazione getTipo() {
        return tipo;
    }

    public void setTipo(TipoPartecipazione tipo) {
        this.tipo = tipo;
    }

    public Utente getUtente() {
        return utente;
    }

    public void setUtente(Utente utente) {
        this.utente = utente;
    }

    public Integer getSquadra() {
        return squadra;
    }

    public void setSquadra(Integer squadra) {
        this.squadra = squadra;
    }

    public Boolean getInvitato() {
        return invitato;
    }

    public void setInvitato(Boolean invitato) {
        this.invitato = invitato;
    }

    public Boolean getConfermata() {
        return confermata;
    }

    public void setConfermata(Boolean confermata) {
        this.confermata = confermata;
    }

    public Integer getPuntiAssegnati() {
        return puntiAssegnati;
    }

    public void setPuntiAssegnati(Integer puntiAssegnati) {
        this.puntiAssegnati = puntiAssegnati;
    }
}
