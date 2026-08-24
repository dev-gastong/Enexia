package com.enexia.rg.model;

/**
 * Vocabulario de roles del sistema (RF-1.3).
 *
 * Los roles viven en la tabla {@code rol} porque son datos, pero el codigo
 * necesita referirse a ellos sin escribir strings sueltos. Este enum es la
 * unica fuente de esos nombres: si manana se renombra un rol, se cambia aca.
 */
public enum RolNombre {
    PARTICIPANTE,
    ORGANIZADOR,
    ADMINISTRADOR
}
