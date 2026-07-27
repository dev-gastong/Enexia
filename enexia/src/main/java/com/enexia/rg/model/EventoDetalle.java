package com.enexia.rg.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "evento_detalle")
@Getter
@Setter
@NoArgsConstructor
public class EventoDetalle {

    @Id
    @Column(name = "id_evento")
    private Long idEvento;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id_evento")
    private Evento evento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_ubicacion")
    private Ubicacion ubicacion;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;
}
