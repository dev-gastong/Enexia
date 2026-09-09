package com.enexia.rg.model;

/**
 * Estados que gestiona el propio organizador (RF-2.8, RF-2.9).
 *
 * Se corresponden con la columna {@code estado_organizador} del catalogo
 * {@code evento_estado_organizador}.
 */
public enum EstadoEventoOrganizadorNombre {

    /** Intencion de publicar. Es el estado con el que nace todo evento. */
    PUBLICADO,

    /**
     * El evento no se realiza pero el registro se conserva (RF-2.9).
     * Dispara la invalidacion de inscripciones y la devolucion de pagos.
     */
    CANCELADO,

    /** Baja logica: el organizador lo retira del catalogo (RF-2.9). Nunca se borra la fila. */
    DADO_DE_BAJA,

    /** Todas sus fechas ya pasaron. Habilita valoraciones (Modulo 3). */
    FINALIZADO
}
