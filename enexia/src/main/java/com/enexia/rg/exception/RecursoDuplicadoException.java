package com.enexia.rg.exception;

/**
 * El email o el nickname ya estan tomados (DFD Registro, salida Return1).
 *
 * Aca SI se informa el conflicto aunque revele existencia de cuenta: no hay
 * alternativa funcional, el usuario necesita saber cual de los dos campos
 * cambiar para poder completar el registro.
 */
public class RecursoDuplicadoException extends RuntimeException {

    public RecursoDuplicadoException(String mensaje) {
        super(mensaje);
    }
}
