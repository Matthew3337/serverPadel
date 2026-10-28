package com.padelbooking.api.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

// Storico delle notifiche push inviate: il vincolo unico (destinatario, partita, tipo)
// impedisce di inviare due volte la stessa notifica.
@Entity
@Table(name = "notifica_push")
public class NotificaPush {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "telefono_destinatario", referencedColumnName = "telefono", nullable = false)
    private Utente destinatario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_partita", nullable = false)
    private Partita partita;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(nullable = false)
    private TipoNotifica tipo;

    // Valorizzata da Java: Hibernate inserisce sempre la colonna, quindi il DEFAULT di MySQL non scatterebbe
    @Column(name = "data_invio", nullable = false)
    private LocalDateTime dataInvio = LocalDateTime.now();

    public NotificaPush() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Utente getDestinatario() {
        return destinatario;
    }

    public void setDestinatario(Utente destinatario) {
        this.destinatario = destinatario;
    }

    public Partita getPartita() {
        return partita;
    }

    public void setPartita(Partita partita) {
        this.partita = partita;
    }

    public TipoNotifica getTipo() {
        return tipo;
    }

    public void setTipo(TipoNotifica tipo) {
        this.tipo = tipo;
    }

    public LocalDateTime getDataInvio() {
        return dataInvio;
    }

    public void setDataInvio(LocalDateTime dataInvio) {
        this.dataInvio = dataInvio;
    }
}
