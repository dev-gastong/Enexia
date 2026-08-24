package com.enexia.rg.exception;

/**
 * La cuenta llego al umbral de bloqueo por intentos fallidos (DFD Login 1.2.7).
 *
 * A diferencia de {@link CredencialesInvalidasException}, aca SI se informa el
 * motivo: el usuario legitimo necesita saber por que no puede entrar, y a esta
 * altura el atacante ya sabe que la cuenta existe.
 */
public class CuentaBloqueadaException extends RuntimeException {

    public CuentaBloqueadaException() {
        super("Cuenta bloqueada por seguridad. Contacte al administrador para restablecerla.");
    }
}
