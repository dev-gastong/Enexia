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
@Table(name = "usuario_estado_sistema")
@Getter
@Setter
@NoArgsConstructor
public class UsuarioEstadoSistema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estado_usuario_sistema")
    private Long idEstadoUsuarioSistema;

    @Column(name = "estado_usuario_sistema")
    private String estadoUsuarioSistema;
}
