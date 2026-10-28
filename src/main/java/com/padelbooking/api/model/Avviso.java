package com.padelbooking.api.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

// Avviso nella bacheca dell'amministratore, sempre relativo a una partita.
@Entity
@Table(name = "avviso")
public class Avviso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_partita", nullable = false)
    private Partita partita;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(nullable = false)
    private TipoAvviso tipo;

    // Valorizzata da Java: Hibernate inserisce sempre la colonna, quindi il DEFAULT di MySQL non scatterebbe
    @Column(name = "data_creazione", nullable = false)
    private LocalDateTime dataCreazione = LocalDateTime.now();

    @Column(nullable = false)
    private Boolean letto = false;

    public Avviso() {
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

    public TipoAvviso getTipo() {
        return tipo;
    }

    public void setTipo(TipoAvviso tipo) {
        this.tipo = tipo;
    }

    public LocalDateTime getDataCreazione() {
        return dataCreazione;
    }

    public void setDataCreazione(LocalDateTime dataCreazione) {
        this.dataCreazione = dataCreazione;
    }

    public Boolean getLetto() {
        return letto;
    }

    public void setLetto(Boolean letto) {
        this.letto = letto;
    }
}
