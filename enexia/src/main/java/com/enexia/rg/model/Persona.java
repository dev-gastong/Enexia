package com.enexia.rg.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Identidad de una PERSONA HUMANA. Es el supertipo de {@link PersonaFisica} y
 * la unica identidad que puede dar origen a un {@link Usuario} (login).
 *
 * POR QUE YA NO EXISTE {@code tipo_persona} (decision Sprint 2, 2026-09-08)
 * La columna nacio suponiendo una jerarquia {@code Persona -> (Fisica |
 * Juridica)}, pero esa jerarquia nunca existio: el MER solo declara
 * {@code Persona ||--|| Persona_Fisica}, y {@link PersonaJuridica} es una
 * entidad independiente sin FK a esta tabla. La columna quedaba entonces con un
 * unico valor posible ("FISICA") en el 100% de las filas: no discriminaba nada,
 * y encima sugeria un modelo que el codigo no implementa. Peor todavia, admitir
 * el valor "JURIDICA" habria abierto la puerta a crear una Persona juridica sin
 * PersonaFisica asociada, rompiendo el invariante de que todo Usuario es una
 * persona humana.
 *
 * El principio que reemplaza a la columna: una Persona Juridica NO es una
 * identidad de acceso, es un CONTENEDOR ADMINISTRATIVO (RF-7.2). Se vincula a
 * las personas humanas a traves de {@code Miembros_Organizacion}, nunca por
 * herencia. Quien inicia sesion, participa y organiza es siempre una persona.
 */
@Entity
@Table(name = "persona")
@Getter
@Setter
@NoArgsConstructor
public class Persona {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_persona")
    private Long idPersona;

    @Column(name = "fecha_registro")
    private LocalDateTime fechaRegistro;
}
