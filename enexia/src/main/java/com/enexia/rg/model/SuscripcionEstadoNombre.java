package com.enexia.rg.model;

/**
 * Vocabulario de estados de una suscripcion (Modulo 8, RF-8.1; panel RF-6.4).
 * Se corresponde con las filas de la tabla {@code suscripcion_estado}.
 */
public enum SuscripcionEstadoNombre {
    /** Upgrade solicitado, pago todavia sin confirmar. */
    PENDIENTE,
    /** Plan vigente: da los privilegios del tipo_plan. */
    ACTIVA,
    /** Venció la cobertura (fecha_fin) sin renovarse. */
    VENCIDA,
    /** Dada de baja: por el titular o revocada por un administrador. */
    CANCELADA
}
