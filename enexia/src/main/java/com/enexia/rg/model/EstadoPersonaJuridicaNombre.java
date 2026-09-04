package com.enexia.rg.model;

/**
 * Vocabulario de estados de una Persona Juridica gestionados por sus propios
 * miembros (ADMINISTRADOR via Miembros_Organizacion), analogo a como
 * {@link EstadoUsuarioNombre} lo gestiona el propio Usuario.
 *
 * Se corresponde con las filas de la tabla {@code persona_juridica_estado}.
 */
public enum EstadoPersonaJuridicaNombre {
    /** Habilitada para operar (siempre que ademas este APROBADO a nivel sistema). */
    ACTIVO,
    /** Baja logica pedida por la organizacion; no participa del catalogo. */
    INACTIVO
}
