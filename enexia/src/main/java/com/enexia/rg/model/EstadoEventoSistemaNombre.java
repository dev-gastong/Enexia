package com.enexia.rg.model;

/**
 * Estados de MODERACION de un evento (RF-2.2, DFD "Flujo de Estados del Evento").
 *
 * Se corresponden con la columna {@code estado_sistema} del catalogo
 * {@code evento_estado_sistema}. Los mueve el sistema o un administrador, nunca
 * el organizador.
 */
public enum EstadoEventoSistemaNombre {

    /** Skeleton recien creado; el pipeline asincrono todavia no dictamino (DFD 2.4). */
    EN_PROCESO,

    /** Texto e imagenes aprobados. Unico estado de sistema visible en el catalogo. */
    APROBADO_SISTEMA,

    /** Rechazado automaticamente. Queda oculto y a la espera de revision manual. */
    RECHAZADO_SISTEMA,

    /**
     * Un administrador revirtio un rechazo automatico, o restituyo un evento
     * que habia suspendido (RF-6.1, escenarios a y b). Tambien visible.
     */
    APROBADO_MANUAL,

    /**
     * Un administrador confirmo el rechazo automatico (RF-6.1a, con
     * {@code motivo_codigo = null}), o suspendio un evento que ya estaba
     * aprobado por una denuncia, fraude o infraccion post-publicacion
     * (RF-6.1b, con un motivo de {@link MotivoSuspensionAdmin}). Reversible:
     * un administrador puede volver a pasarlo a APROBADO_MANUAL.
     */
    RECHAZADO_MANUAL,

    /**
     * Cambio de contenido sensible en un evento ya publicado (RF-2.7): la version
     * anterior sigue en el catalogo mientras se remodera el borrador.
     */
    EN_REVISION
}
