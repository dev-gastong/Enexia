package com.enexia.rg.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "evento")
@Getter
@Setter
@NoArgsConstructor
public class Evento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evento")
    private Long idEvento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_organizador")
    private Usuario organizador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_categoria")
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_estado_sistema")
    private EventoEstadoSistema estadoSistema;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_estado_organizador")
    private EventoEstadoOrganizador estadoOrganizador;

    @Column(name = "nombre")
    private String nombre;

    @Column(name = "url_portada")
    private String urlPortada;
}
