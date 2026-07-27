package com.enexia.rg.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "historial_interacciones")
@Getter
@Setter
@NoArgsConstructor
public class HistorialInteracciones {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historial")
    private Long idHistorial;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @Column(name = "accion")
    private String accion;

    @Column(name = "modulo")
    private String modulo;

    @Column(name = "endpoint")
    private String endpoint;

    @Column(name = "metodo_http")
    private String metodoHttp;

    @Column(name = "detalles", columnDefinition = "TEXT")
    private String detalles;

    @Column(name = "ip_origen")
    private String ipOrigen;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "fecha_interaccion")
    private LocalDateTime fechaInteraccion;
}
