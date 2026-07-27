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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "persona_fisica")
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
