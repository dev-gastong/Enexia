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
@Table(name = "evento_estado_organizador")
@Getter
@Setter
@NoArgsConstructor
public class EventoEstadoOrganizador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estado_organizador")
    private Long idEstadoOrganizador;

    @Column(name = "estado_organizador")
    private String estadoOrganizador;
}
