package com.enexia.rg.exception;

import java.time.LocalDateTime;

import lombok.Getter;

/**
 * La cuenta esta penalizada temporalmente y todavia no venció el cooldown
 * (DFD Login, salida Err_Cool).
 */
@Getter
public class CuentaEnCooldownException extends RuntimeException {

    /** Momento a partir del cual el usuario puede volver a intentar. */
    private final LocalDateTime disponibleDesde;

    public CuentaEnCooldownException(LocalDateTime disponibleDesde) {
        super("Cuenta penalizada temporalmente por intentos fallidos. Aguarde unos minutos.");
        this.disponibleDesde = disponibleDesde;
    }
}
