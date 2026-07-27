package com.enexia.rg.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "inscripcion_estado")
@Getter
@Setter
@NoArgsConstructor
public class InscripcionEstado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estado_inscripcion")
    private Long idEstadoInscripcion;

    @Column(name = "nombre_estado")
    private String nombreEstado;
}
