package com.enexia.rg.model;

/**
 * Vocabulario de {@code motivo_codigo} para la SUSPENSION DISCIPLINARIA de un
 * evento (RF-6.1, escenario b): un administrador retira del catalogo un
 * evento que ya estaba {@code APROBADO_SISTEMA} o {@code APROBADO_MANUAL},
 * por una denuncia, fraude o infraccion detectada despues de la publicacion.
 *
 * Es un enum aparte de {@link MotivoModeracionEvento} a proposito: ese otro
 * vocabulario es exclusivo del pipeline AUTOMATICO (rechaza antes de
 * publicar). Mezclar ambos en un solo enum volveria ambiguo, al leer el
 * historial de un evento, si "RECHAZADO_SISTEMA/MODERACION_TEXTO" fue una
 * decision del software o una sancion manual sobre algo que ya estaba al aire.
 */
public enum MotivoSuspensionAdmin {

    /** Una denuncia de un usuario o un tercero resulto fundada tras revision manual. */
    DENUNCIA_FUNDADA,

    /** El organizador o el evento resultaron fraudulentos (entradas falsas, evento inexistente). */
    FRAUDE_DETECTADO,

    /** El evento infringio una norma despues de ya estar publicado (cambio de contenido fuera del flujo de edicion, denuncia de terceros, etc.). */
    INFRACCION_POST_PUBLICACION
}
