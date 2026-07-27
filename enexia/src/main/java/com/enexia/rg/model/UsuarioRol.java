package com.enexia.rg.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "usuario_rol")
@Getter
@Setter
@NoArgsConstructor
public class UsuarioRol {

    @EmbeddedId
    private UsuarioRolId id = new UsuarioRolId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("idUsuario")
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("idRol")
    @JoinColumn(name = "id_rol")
    private Rol rol;
}
