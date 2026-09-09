package com.enexia.rg.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * El UNIQUE de dni implementa el paso 7.1.2 del DFD ("Error 409: DNI ya
 * Registrado") a nivel de base. El existsByDni del service da el mensaje
 * amigable en el caso normal, pero es este indice el que garantiza la unicidad
 * cuando dos altas con el mismo documento corren en paralelo: email y nickname
 * identifican a la cuenta, el DNI identifica a la persona.
 */
@Entity
@Table(name = "persona_fisica", uniqueConstraints =
        @UniqueConstraint(name = "uk_persona_fisica_dni", columnNames = "dni"))
@Getter
@Setter
@NoArgsConstructor
public class PersonaFisica {

    @Id
    @Column(name = "id_persona")
    private Long idPersona;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id_persona")
    private Persona persona;

    @Column(name = "dni")
    private String dni;

    @Column(name = "nombre")
    private String nombre;

    @Column(name = "apellido")
    private String apellido;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;
}
