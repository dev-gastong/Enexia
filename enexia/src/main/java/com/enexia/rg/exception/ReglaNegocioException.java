package com.enexia.rg.exception;

/**
 * Violacion de una regla de negocio que las anotaciones de validacion no pueden
 * expresar por si solas: por ejemplo que password y passwordConfirmacion
 * coincidan, o que el catalogo de roles tenga cargado el rol solicitado.
 */
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
