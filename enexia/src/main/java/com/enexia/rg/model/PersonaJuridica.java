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
@Table(name = "persona_juridica")
@Getter
@Setter
@NoArgsConstructor
public class PersonaJuridica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_persona_juridica")
    private Long idPersonaJuridica;

    @Column(name = "razon_social")
    private String razonSocial;

    @Column(name = "nombre_fantasia")
    private String nombreFantasia;

    @Column(name = "cuit")
    private String cuit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_ubicacion")
    private Ubicacion ubicacion;

    @Column(name = "emailCorporativo")
    private String emailCorporativo;

    @Column(name = "telefonoContacto")
    private String telefonoContacto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_estado_persona_juridica_sistema")
    private PersonaJuridicaEstadoSistema estadoPersonaJuridicaSistema;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_estado_persona_juridica")
    private PersonaJuridicaEstado estadoPersonaJuridica;

    @Column(name = "fecha_registro")
    private LocalDateTime fechaRegistro;
}
