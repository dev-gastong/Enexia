package com.enexia.rg.exception;

/**
 * La operacion esta prohibida por una regla de dominio y NO por falta de
 * permisos de rol: por ejemplo, editar el precio de un ticket que ya tiene
 * inscripciones activas (RF-2.7).
 *
 * Se distingue de {@link ReglaNegocioException} (400, dato mal formado o
 * incoherente) porque aca el dato es correcto: lo que no corresponde es la
 * accion en el estado actual del recurso. Se traduce a 409 Conflict.
 */
public class OperacionNoPermitidaException extends RuntimeException {

    public OperacionNoPermitidaException(String mensaje) {
        super(mensaje);
    }
}
