package com.enexia.rg.model;

/**
 * Codigos de las notificaciones por correo (DFD notificaciones, Nivel 1).
 *
 * Es el valor que se persiste en {@code notificacion_enviada.tipo}: renombrar
 * un codigo aca equivale a perder el historial de idempotencia del anterior,
 * asi que se agregan valores nuevos y no se renombran los existentes.
 */
public enum NotificacionTipo {
    RECUPERACION,
    BLOQUEO,
    PASSWORD_CAMBIADA,
    CUENTA_SUSPENDIDA,
    MODERACION_RESULTADO,
    EVENTO_SUSPENDIDO,
    EVENTO_REACTIVADO,
    INSCRIPCION_CONFIRMADA,
    EVENTO_CANCELADO,
    RECORDATORIO_10MIN,
    INVITACION_VALORAR,
    AGREGADO_A_EQUIPO
}
