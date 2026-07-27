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
@Table(name = "evento_estado_sistema")
@Getter
@Setter
@NoArgsConstructor
public class EventoEstadoSistema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estado_sistema")
    private Long idEstadoSistema;

    @Column(name = "estado_sistema")
    private String estadoSistema;

    @Column(name = "motivo_codigo")
    private String motivoCodigo;
}
