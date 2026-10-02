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
    private Long idCronograma;
    private LocalDate fechaCronograma;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    /** PENDIENTE, PENDIENTE_PAGO, CONFIRMADA o CANCELADA. */
    private String estado;
    private LocalDate fechaInscripcion;
    private BigDecimal precioAbonado;

    /**
     * Codigo de ingreso (RF-3.6). Solo se completa cuando {@code estado} es
     * CONFIRMADA: una inscripcion cancelada o pendiente no habilita el acceso.
     */
    private String codigoQr;

    /**
     * Si el usuario ya valoro este cronograma (RF-3.4). El historial la
     * necesita para decidir si ofrece "Dejar valoracion" o no -- sin esto,
     * el frontend solo se enteraria de la duplicada cuando el POST a
     * /api/participante/valoraciones ya rechazo con 409.
     */
    private boolean yaValorado;

    private List<HistorialEstadoItem> historial;
}
