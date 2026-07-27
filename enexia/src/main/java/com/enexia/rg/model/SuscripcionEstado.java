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
@Table(name = "suscripcion_estado")
@Getter
@Setter
@NoArgsConstructor
public class SuscripcionEstado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estado_suscripcion")
    private Long idEstadoSuscripcion;

    @Column(name = "nombre_estado")
    private String nombreEstado;
}
