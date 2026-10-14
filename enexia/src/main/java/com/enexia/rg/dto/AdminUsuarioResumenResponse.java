package com.enexia.rg.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Totales de las tarjetas del directorio de usuarios (RF-6.2), sobre TODA la base y no sobre la pagina. */
@Getter
@AllArgsConstructor
public class AdminUsuarioResumenResponse {

    private long totalUsuarios;
    private long cuentasSuspendidas;
}
