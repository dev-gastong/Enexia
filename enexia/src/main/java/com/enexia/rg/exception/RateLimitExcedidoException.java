package com.enexia.rg.exception;

/**
 * Una IP supero el cupo de intentos permitidos en la ventana de tiempo
 * (DFD Login 1.2.1, salida Err_429).
 *
 * Frena ataques de fuerza bruta distribuidos entre muchas cuentas: el bloqueo
 * por intentos fallidos protege UNA cuenta, este control protege al sistema
 * de una IP que prueba miles de emails distintos.
 */
public class RateLimitExcedidoException extends RuntimeException {

    public RateLimitExcedidoException() {
        super("Demasiados intentos desde esta direccion. Intente nuevamente mas tarde.");
    }
}
