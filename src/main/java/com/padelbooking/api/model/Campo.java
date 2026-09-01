package com.padelbooking.api.model;

import jakarta.persistence.*;

import java.time.LocalTime;

@Entity
@Table(name = "campo")
public class Campo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(name = "al_coperto", nullable = false)
    private Boolean alCoperto;

    @Column(name = "ora_apertura", nullable = false)
    private LocalTime oraApertura;

    @Column(name = "ora_chiusura", nullable = false)
    private LocalTime oraChiusura;

    public Campo() {
    }

    public Campo(Integer id, String nome, Boolean alCoperto, LocalTime oraApertura, LocalTime oraChiusura) {
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
