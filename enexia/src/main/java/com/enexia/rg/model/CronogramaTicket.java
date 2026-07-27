package com.enexia.rg.model;

import java.math.BigDecimal;

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
@Table(name = "cronograma_ticket")
@Getter
@Setter
@NoArgsConstructor
public class CronogramaTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cronograma_ticket")
    private Long idCronogramaTicket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cronograma")
    private EventoCronograma cronograma;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tipo_ticket")
    private TipoTicket tipoTicket;

    @Column(name = "precio")
    private BigDecimal precio;

    @Column(name = "cupo_maximo")
    private Integer cupoMaximo;

    @Column(name = "cupo_actual")
    private Integer cupoActual;
}
