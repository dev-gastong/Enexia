package com.enexia.rg.exception;

import java.time.LocalDateTime;

import lombok.Getter;

/**
 * La cuenta esta penalizada temporalmente y el cooldown todavia no vencio.
 *
 * CAMBIO DE POLITICA (2026-09-08)
 * {@code disponibleDesde} ya NO se publica en la cabecera X-Reintentar-Despues.
 * Esa cabecera era un oraculo perfecto: leyendola desde las DevTools del
 * navegador, un atacante sabia que la cuenta existe, que esta penalizada y el
 * segundo exacto en que puede reanudar el ataque. Ahora el dato queda solo del
 * lado del servidor, para el log y la auditoria.
 */
@Getter
public class CuentaEnCooldownException extends AutenticacionFallidaException {

    public static final String CODIGO = "CUENTA_EN_COOLDOWN";

    /** Momento en que expira la penalizacion. Uso interno: nunca se serializa. */
    private final LocalDateTime disponibleDesde;

    public CuentaEnCooldownException(LocalDateTime disponibleDesde) {
        super(CODIGO);
        this.disponibleDesde = disponibleDesde;
    }
}
