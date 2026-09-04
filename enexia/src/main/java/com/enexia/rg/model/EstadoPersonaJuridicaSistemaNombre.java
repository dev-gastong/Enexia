package com.enexia.rg.model;

/**
 * Vocabulario de estados de moderacion de una Persona Juridica.
 *
 * Se corresponde con las filas de la tabla {@code persona_juridica_estado_sistema}.
 * Lo gestiona un moderador/administrador, nunca la propia organizacion.
 */
public enum EstadoPersonaJuridicaSistemaNombre {
    /** Recien registrada, esperando que un moderador revise CUIT y razon social. */
    REVISION_PENDIENTE,
    /** El moderador valido los datos; habilita los permisos de publicacion. */
    APROBADO,
    /** El moderador rechazo el alta (CUIT invalido, datos inconsistentes, etc.). */
    RECHAZADO
}
