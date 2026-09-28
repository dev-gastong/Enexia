package com.enexia.rg.model;

/**
 * Vocabulario de estados de un {@link Pago} (RF-3.2, RF-3.3).
 *
 * Se corresponde con las filas de la tabla {@code pago_estado}.
 */
public enum PagoEstadoNombre {
    /** La pasarela simulada aprobo la operacion. */
    COMPLETADO,
    /** La pasarela simulada rechazo la operacion. */
    FALLIDO,
    /** Reembolso simulado por cancelacion de una inscripcion ya pagada (RF-3.3). */
    REEMBOLSADO
}
