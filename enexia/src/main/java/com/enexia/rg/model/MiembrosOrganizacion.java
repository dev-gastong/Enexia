package com.enexia.rg.model;

import jakarta.persistence.Column;
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
@Table(name = "miembros_organizacion")
@Getter
@Setter
@NoArgsConstructor
public class MiembrosOrganizacion {

    @EmbeddedId
    private MiembrosOrganizacionId id = new MiembrosOrganizacionId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("idUsuario")
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("idPersonaJuridica")
    @JoinColumn(name = "id_persona_juridica")
    private PersonaJuridica personaJuridica;

    @Column(name = "rol_en_empresa")
    private String rolEnEmpresa;
}
