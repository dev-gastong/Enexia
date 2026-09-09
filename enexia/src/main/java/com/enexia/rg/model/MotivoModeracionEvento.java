package com.enexia.rg.model;

/**
 * Vocabulario de {@code motivo_codigo} (columna del catalogo
 * {@code evento_estado_sistema}, segun el MER).
 *
 * POR QUE UN CODIGO Y NO UN TEXTO LIBRE
 * El motivo se muestra al organizador y se usa para filtrar en el panel de
 * administracion. Un texto libre haria imposible agrupar ("todos los rechazos
 * por imagen") y ademas tentaria a volcar ahi el detalle del analisis, que no
 * corresponde exponer: decirle a quien sube contenido ofensivo exactamente que
 * termino disparo el filtro es ensenarle a evadirlo.
 */
public enum MotivoModeracionEvento {

    /** Fase 1 del pipeline: titulo o descripcion con lenguaje inapropiado (RF-2.2). */
    MODERACION_TEXTO,

    /** Fase 2: todas las imagenes fueron rechazadas por el analisis de contenido. */
    MODERACION_IMAGEN,

    /** Fase 2: ninguna imagen supero las validaciones de formato o peso (RF-5.2). */
    SIN_IMAGENES_VALIDAS,

    /**
     * El pipeline fallo por un problema tecnico (Cloudinary caido, timeout).
     * Se distingue de los rechazos por contenido a proposito: no es culpa del
     * organizador y amerita reintento, no sancion.
     */
    ERROR_PIPELINE
}
