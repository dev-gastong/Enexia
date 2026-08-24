package com.enexia.rg.exception;

/**
 * Se lanza cuando el email no existe, el usuario no esta ACTIVO o la contrasena
 * no coincide (DFD Login, salidas Err_Gen1 y Err_Gen2).
 *
 * DECISION DE SEGURIDAD: los tres casos comparten una unica excepcion y un unico
 * mensaje. Distinguirlos permitiria enumeracion de cuentas: un atacante probaria
 * emails y sabria cuales estan registrados por la diferencia de respuesta.
 * Por eso el mensaje al cliente siempre es "Email o contrasena incorrectos",
 * y el motivo real solo queda en el log del servidor.
 */
public class CredencialesInvalidasException extends RuntimeException {

    private static final String MENSAJE_GENERICO = "Email o contrasena incorrectos";

    public CredencialesInvalidasException() {
        super(MENSAJE_GENERICO);
    }
}
