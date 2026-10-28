package com.padelbooking.api.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

// La partita è la prenotazione di uno slot: le annullate restano in tabella con stato ANNULLATA.
// La colonna generata slot_attivo non è mappata: la calcola MySQL dallo stato e serve solo
// al vincolo unico uk_partita_slot, il codice Java non la legge mai.
@Entity
@Table(name = "partita")
public class Partita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_campo", nullable = false)
    private Campo campo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "telefono_creatore", referencedColumnName = "telefono", nullable = false)
    private Utente creatore;

    @Column(name = "data_partita", nullable = false)
    private LocalDate dataPartita;

    @Column(name = "ora_inizio", nullable = false)
    private LocalTime oraInizio;

    @Column(name = "ora_fine", nullable = false)
    private LocalTime oraFine;

    // Le colonne ENUM di MySQL vanno dichiarate come tali, altrimenti la validazione
    // di Hibernate si aspetta un VARCHAR e l'avvio fallisce
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(nullable = false)
    private TipoPartita tipo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(nullable = false)
    private StatoPartita stato = StatoPartita.CREATA;

    // Game dei set: TINYINT nello schema, NULL finché il risultato non è registrato
    @Column(name = "set1_sq1", columnDefinition = "TINYINT")
    private Integer set1Sq1;

    @Column(name = "set1_sq2", columnDefinition = "TINYINT")
    private Integer set1Sq2;

    @Column(name = "set2_sq1", columnDefinition = "TINYINT")
    private Integer set2Sq1;

    @Column(name = "set2_sq2", columnDefinition = "TINYINT")
    private Integer set2Sq2;

    @Column(name = "set3_sq1", columnDefinition = "TINYINT")
    private Integer set3Sq1;

    @Column(name = "set3_sq2", columnDefinition = "TINYINT")
    private Integer set3Sq2;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "telefono_registrante", referencedColumnName = "telefono")
    private Utente registrante;

    @Column(name = "data_risultato")
    private LocalDateTime dataRisultato;

    // Il cascade permette di salvare partita e partecipazioni con un solo save,
    // quindi in un'unica transazione
    @OneToMany(mappedBy = "partita", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    private List<Partecipazione> partecipazioni = new ArrayList<>();

    public Partita() {
    }

    // Mantiene coerenti entrambi i lati della relazione
    public void aggiungiPartecipazione(Partecipazione partecipazione) {
        partecipazione.setPartita(this);
        partecipazioni.add(partecipazione);
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

    public Utente getCreatore() {
        return creatore;
    }

    public void setCreatore(Utente creatore) {
        this.creatore = creatore;
    }

    public LocalDate getDataPartita() {
        return dataPartita;
    }

    public void setDataPartita(LocalDate dataPartita) {
        this.dataPartita = dataPartita;
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

    public TipoPartita getTipo() {
        return tipo;
    }

    public void setTipo(TipoPartita tipo) {
        this.tipo = tipo;
    }

    public StatoPartita getStato() {
        return stato;
    }

    public void setStato(StatoPartita stato) {
        this.stato = stato;
    }

    public Integer getSet1Sq1() {
        return set1Sq1;
    }

    public void setSet1Sq1(Integer set1Sq1) {
        this.set1Sq1 = set1Sq1;
    }

    public Integer getSet1Sq2() {
        return set1Sq2;
    }

    public void setSet1Sq2(Integer set1Sq2) {
        this.set1Sq2 = set1Sq2;
    }

    public Integer getSet2Sq1() {
        return set2Sq1;
    }

    public void setSet2Sq1(Integer set2Sq1) {
        this.set2Sq1 = set2Sq1;
    }

    public Integer getSet2Sq2() {
        return set2Sq2;
    }

    public void setSet2Sq2(Integer set2Sq2) {
        this.set2Sq2 = set2Sq2;
    }

    public Integer getSet3Sq1() {
        return set3Sq1;
    }

    public void setSet3Sq1(Integer set3Sq1) {
        this.set3Sq1 = set3Sq1;
    }

    public Integer getSet3Sq2() {
        return set3Sq2;
    }

    public void setSet3Sq2(Integer set3Sq2) {
        this.set3Sq2 = set3Sq2;
    }

    public Utente getRegistrante() {
        return registrante;
    }

    public void setRegistrante(Utente registrante) {
        this.registrante = registrante;
    }

    public LocalDateTime getDataRisultato() {
        return dataRisultato;
    }

    public void setDataRisultato(LocalDateTime dataRisultato) {
        this.dataRisultato = dataRisultato;
    }

    public List<Partecipazione> getPartecipazioni() {
        return partecipazioni;
    }

    public void setPartecipazioni(List<Partecipazione> partecipazioni) {
        this.partecipazioni = partecipazioni;
    }
}
