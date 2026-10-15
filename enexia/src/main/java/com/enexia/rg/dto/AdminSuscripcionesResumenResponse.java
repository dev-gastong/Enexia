package com.enexia.rg.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Tarjetas del control de suscripciones (RF-6.4): totales sobre TODA la base. */
@Getter
@AllArgsConstructor
public class AdminSuscripcionesResumenResponse {

    /** Suscripciones Pro vigentes. */
    private long suscripcionesActivas;
    private long pagosVencidos;
    private BigDecimal ingresosDelMes;
    private List<Plan> planes;

    @Getter
    @AllArgsConstructor
    public static class Plan {
        /** GRATUITO o PRO. */
        private String plan;
        private BigDecimal precioMensual;
        private long usuariosActivos;

        /** Tope de eventos activos; {@code null} = ilimitado (RF-8.2: Plan Pro). */
        private Integer eventosPermitidos;
    }
}
