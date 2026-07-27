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
@Table(name = "historial_estado_usuario")
@Getter
@Setter
@NoArgsConstructor
public class HistorialEstadoUsuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historial")
    private Long idHistorial;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_estado_usuario_sistema")
    private UsuarioEstadoSistema estadoUsuarioSistema;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_estado_usuario")
    private UsuarioEstado estadoUsuario;

    @Column(name = "fecha_cambio")
    private LocalDateTime fechaCambio;
}
