package com.enexia.rg.exception;

/**
 * La cuenta alcanzo el umbral de bloqueo por intentos fallidos (RF-1.4).
 *
 * CAMBIO DE POLITICA (2026-09-08)
 * Antes esta excepcion producia un 403 con el texto "Cuenta bloqueada por
 * seguridad...". Eso le confirmaba al atacante tres cosas de una: que el email
 * existe, que su ataque funciono, y en que momento exacto se disparo el umbral.
 *
 * Ahora se responde igual que cualquier otro fallo de login (401 generico). Al
 * dueno legitimo se le avisa por el unico canal que el atacante no controla: un
 * email con enlace de recuperacion (RF-1.5). Ver RecuperacionCuentaService.
 */
public class CuentaBloqueadaException extends AutenticacionFallidaException {

    public static final String CODIGO = "CUENTA_BLOQUEADA";

    public CuentaBloqueadaException() {
        super(CODIGO);
    }
}
