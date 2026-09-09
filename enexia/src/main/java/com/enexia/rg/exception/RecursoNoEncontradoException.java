package com.enexia.rg.exception;

/**
 * El recurso pedido no existe, o existe pero el solicitante no tiene derecho a
 * saber que existe (404 en vez de 403 -- ver mas abajo).
 *
 * POR QUE 404 Y NO 403 EN RECURSOS AJENOS
 * Cuando un organizador pide un evento que no le pertenece, responder 403
 * ("existe pero no es tuyo") le confirma que ese id esta ocupado. Iterando ids
 * podria mapear cuantos eventos hay en la plataforma y cuales pertenecen a
 * quien. Un 404 uniforme no distingue "no existe" de "no es tuyo".
 */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
