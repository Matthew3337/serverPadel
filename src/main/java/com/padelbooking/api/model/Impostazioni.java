package com.padelbooking.api.model;

import jakarta.persistence.*;

// Tabella a riga singola (id = 1, garantito da un CHECK): contiene il livello unico
// dei giocatori campo (RF_GA_14). L'id non è generato perché la riga esiste già dallo script SQL.
@Entity
@Table(name = "impostazioni")
public class Impostazioni {

    @Id
    @Column(columnDefinition = "TINYINT")
    private Integer id;

    @Column(name = "livello_giocatori_campo", nullable = false)
    private Double livelloGiocatoriCampo = 1.0;

    public Impostazioni() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Double getLivelloGiocatoriCampo() {
        return livelloGiocatoriCampo;
    }

    public void setLivelloGiocatoriCampo(Double livelloGiocatoriCampo) {
        this.livelloGiocatoriCampo = livelloGiocatoriCampo;
    }
}
