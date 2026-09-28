package com.enexia.rg.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una inscripcion tal como la ve su duenio: confirmacion al crearla (RF-3.1,
 * RF-3.2) y fila del historial personal (RF-3.6).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InscripcionResponse {

    private Long idInscripcion;
    private Long idEvento;
    private String eventoNombre;
    private String tipoTicket;
    private LocalDate fechaCronograma;
    private LocalTime horaInicio;
    /** PENDIENTE, PENDIENTE_PAGO, CONFIRMADA o CANCELADA. */
    private String estado;
    private LocalDate fechaInscripcion;
    private BigDecimal precioAbonado;

    /**
     * Codigo de ingreso (RF-3.6). Solo se completa cuando {@code estado} es
     * CONFIRMADA: una inscripcion cancelada o pendiente no habilita el acceso.
     */
    private String codigoQr;

    private List<HistorialEstadoItem> historial;
}
