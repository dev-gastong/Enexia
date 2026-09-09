package com.enexia.rg.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Un tipo de ticket con su disponibilidad (RF-2.6, RF-4.4). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketResponse {

    private Long idCronogramaTicket;
    private String tipoTicket;
    private BigDecimal precio;
    private Integer cupoMaximo;
    private Integer cupoActual;

    /**
     * Lugares que quedan. Se calcula en el servidor y no en el frontend para que
     * la regla de "agotado" sea una sola: si cada cliente restara por su cuenta,
     * bastaria un cupoActual nulo para que una pantalla mostrara disponibilidad
     * donde otra muestra agotado.
     */
    private Integer cupoDisponible;

    private boolean agotado;

    /** Derivado de precio == 0 (RF-2.6). */
    private boolean gratuito;
}
