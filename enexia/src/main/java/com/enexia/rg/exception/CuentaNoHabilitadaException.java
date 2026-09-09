package com.enexia.rg.exception;

import lombok.Getter;

/**
 * La cuenta existe y las credenciales podrian ser correctas, pero su estado no
 * habilita el ingreso: SUSPENDIDO (sancion de un administrador) o DE_BAJA
 * (baja logica solicitada por el usuario) -- RF-1.6.
 *
 * Se separa de {@link CuentaBloqueadaException} para poder auditarlas distinto:
 * un SUSPENDIDO es una decision humana y un BLOQUEADO es automatico. Hacia
 * afuera, las dos responden el mismo 401 generico.
 */
@Getter
public class CuentaNoHabilitadaException extends AutenticacionFallidaException {

    /** Estado real de la cuenta. Uso interno: nunca se serializa. */
    private final String estadoReal;

    public CuentaNoHabilitadaException(String estadoReal) {
        super("CUENTA_" + (estadoReal == null ? "SIN_ESTADO" : estadoReal.toUpperCase()));
        this.estadoReal = estadoReal;
    }
}
