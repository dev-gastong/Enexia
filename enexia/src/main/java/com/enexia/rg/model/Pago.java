package com.enexia.rg.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pago")
@Getter
@Setter
@NoArgsConstructor
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pago")
    private Long idPago;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_inscripcion")
    private Inscripcion inscripcion;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_suscripcion")
    private Suscripcion suscripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_estado_pago")
    private PagoEstado estadoPago;

    @Column(name = "monto")
    private BigDecimal monto;

    @Column(name = "metodo_pago")
    private String metodoPago;

    @Column(name = "token_operacion")
    private String tokenOperacion;

    @Column(name = "fecha_pago")
    private LocalDateTime fechaPago;
}
