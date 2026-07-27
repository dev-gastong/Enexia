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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Long idUsuario;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_persona_fisica")
    private PersonaFisica personaFisica;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_estado_usuario_sistema")
    private UsuarioEstadoSistema estadoUsuarioSistema;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_estado_usuario")
    private UsuarioEstado estadoUsuario;

    @Column(name = "email")
    private String email;

    @Column(name = "password")
    private String password;

    @Column(name = "nickname")
    private String nickname;

    // Campos de control de seguridad del login (RF-1.4 / DFD Login)
    @Column(name = "intentos_fallidos")
    private Integer intentosFallidos;

    @Column(name = "requiere_captcha")
    private Boolean requiereCaptcha;

    @Column(name = "fecha_desbloqueo_cooldown")
    private LocalDateTime fechaDesbloqueoCooldown;

    @Column(name = "fecha_baja")
    private LocalDateTime fechaBaja;
}
