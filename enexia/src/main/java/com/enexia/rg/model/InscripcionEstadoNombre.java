package com.enexia.rg.model;

/**
 * Vocabulario de estados de una {@link Inscripcion} (RF-3.1 a RF-3.3).
 *
 * Se corresponde con las filas de la tabla {@code inscripcion_estado}.
 */
public enum InscripcionEstadoNombre {
    /** Reserva recien creada, cupo todavia sin confirmar (RF-3.1). */
    PENDIENTE,
    /** Ticket de pago cuya pasarela simulada rechazo la operacion (RF-3.2). */
    PENDIENTE_PAGO,
    /** Cupo confirmado: ticket gratuito directo, o pago aprobado (RF-3.1, RF-3.2). */
    CONFIRMADA,
    /** Borrado logico por cancelacion voluntaria del participante (RF-3.3). */
    CANCELADA
}
