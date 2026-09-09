package com.enexia.rg.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Los UNIQUE de email y nickname son la defensa REAL contra el registro
 * duplicado, no los existsBy... del service. Bajo concurrencia, dos altas
 * simultaneas pasan ambas el chequeo previo (ninguna ve la fila aun sin
 * confirmar de la otra) y solo el indice de la base, atomico, frena a la
 * segunda. La collation utf8mb4_unicode_ci hace que el UNIQUE respete el
 * "ignore case" con el que el service compara.
 */
@Entity
@Table(name = "usuario", uniqueConstraints = {
        @UniqueConstraint(name = "uk_usuario_email", columnNames = "email"),
        @UniqueConstraint(name = "uk_usuario_nickname", columnNames = "nickname")
})
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

    @OneToMany(mappedBy = "usuario", fetch = FetchType.EAGER)
    private Set<UsuarioRol> usuarioRoles;

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

    public List<String> getRoles() {
        if (usuarioRoles == null || usuarioRoles.isEmpty()) {
            return List.of();
        }
        return usuarioRoles.stream()
            .map(ur -> ur.getRol().getNombreRol())
            .collect(Collectors.toList());
    }
}
