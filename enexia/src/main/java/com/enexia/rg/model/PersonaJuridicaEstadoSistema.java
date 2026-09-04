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
@Table(name = "persona_juridica_estado_sistema")
@Getter
@Setter
@NoArgsConstructor
public class PersonaJuridicaEstadoSistema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estado_persona_juridica_sistema")
    private Long idEstadoPersonaJuridicaSistema;

    @Column(name = "estado_persona_juridica_sistema")
    private String estadoPersonaJuridicaSistema;
}
