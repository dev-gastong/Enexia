package com.enexia.rg.model;

/**
 * Vocabulario de estados de cuenta gestionados por el propio usuario y por el
 * control de seguridad del login (RF-1.6).
 *
 * Se corresponde con las filas de la tabla {@code usuario_estado}.
 *
 * NOTA DE ALCANCE: el MER define ademas {@code usuario_estado_sistema} para los
 * estados de moderacion. Por decision del 2026-08-24 el login de Sprint 1 solo
 * consulta {@code usuario_estado}; {@code usuario_estado_sistema} se incorporara
 * cuando se implemente el modulo de moderacion.
 */
public enum EstadoUsuarioNombre {
    /** Unico estado que habilita el login. */
    ACTIVO,
    /** Bloqueo automatico por acumulacion de intentos fallidos (DFD Login 1.2.7). */
    BLOQUEADO,
    /** Suspension aplicada por un administrador. */
    SUSPENDIDO,
    /** Baja logica solicitada por el usuario; se acompana de {@code fecha_baja}. */
    DE_BAJA
}
