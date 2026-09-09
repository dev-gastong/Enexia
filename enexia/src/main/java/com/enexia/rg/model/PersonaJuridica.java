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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Organizacion (empresa, institucion, ONG) que respalda eventos: RF-7.2.
 *
 * NO ES UNA IDENTIDAD DE ACCESO. No tiene FK a {@link Persona}, no tiene
 * {@link Usuario} propio y no puede iniciar sesion ni inscribirse a un evento.
 * Es un CONTENEDOR ADMINISTRATIVO: se relaciona con personas humanas a traves
 * de {@code miembros_organizacion}, y quien organiza de verdad es siempre uno
 * de esos miembros. Por eso {@code Persona} dejo de tener {@code tipo_persona}
 * el 2026-09-08: nunca hubo herencia entre ambas.
 *
 * El UNIQUE de cuit es la defensa real contra dos organizaciones con el mismo
 * numero fiscal. El existsByCuit del service da el mensaje amigable; el indice
 * es el que sostiene la regla cuando dos altas corren en paralelo. Se compara
 * sobre el CUIT NORMALIZADO (solo digitos): sin eso, "30-71659554-0" y
 * "30716595540" serian dos filas distintas y el UNIQUE no serviria de nada.
 */
@Entity
@Table(name = "persona_juridica", uniqueConstraints =
        @UniqueConstraint(name = "uk_persona_juridica_cuit", columnNames = "cuit"))
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
