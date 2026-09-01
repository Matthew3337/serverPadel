package com.padelbooking.api.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "prenotazione")
public class Prenotazione {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_campo", nullable = false)
    private Campo campo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_giocatore1", nullable = false)
    private Utente giocatore1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_giocatore2")
    private Utente giocatore2;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_giocatore3")
    private Utente giocatore3;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_giocatore4")
    private Utente giocatore4;

    @Column(name = "data_prenotazione", nullable = false)
    private LocalDate dataPrenotazione;

    @Column(name = "ora_inizio", nullable = false)
    private LocalTime oraInizio;

    @Column(name = "ora_fine", nullable = false)
    private LocalTime oraFine;

    public Prenotazione() {
    }

    public Prenotazione(Integer id, Campo campo, Utente giocatore1, Utente giocatore2, Utente giocatore3,
                         Utente giocatore4, LocalDate dataPrenotazione, LocalTime oraInizio, LocalTime oraFine) {
        this.id = id;
        this.campo = campo;
        this.giocatore1 = giocatore1;
        this.giocatore2 = giocatore2;
        this.giocatore3 = giocatore3;
        this.giocatore4 = giocatore4;
        this.dataPrenotazione = dataPrenotazione;
        this.oraInizio = oraInizio;
        this.oraFine = oraFine;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Campo getCampo() {
        return campo;
    }

    public void setCampo(Campo campo) {
        this.campo = campo;
    }

    public Utente getGiocatore1() {
        return giocatore1;
    }

    public void setGiocatore1(Utente giocatore1) {
        this.giocatore1 = giocatore1;
    }

    public Utente getGiocatore2() {
        return giocatore2;
    }

    public void setGiocatore2(Utente giocatore2) {
        this.giocatore2 = giocatore2;
    }

    public Utente getGiocatore3() {
        return giocatore3;
    }

    public void setGiocatore3(Utente giocatore3) {
        this.giocatore3 = giocatore3;
    }

    public Utente getGiocatore4() {
        return giocatore4;
    }

    public void setGiocatore4(Utente giocatore4) {
        this.giocatore4 = giocatore4;
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
}
