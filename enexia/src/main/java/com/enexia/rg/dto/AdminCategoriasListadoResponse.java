package com.enexia.rg.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Listado completo del ABM (son pocas) mas los totales de las tarjetas del panel. */
@Getter
@AllArgsConstructor
public class AdminCategoriasListadoResponse {

    private long totalCategorias;
    private long categoriasActivas;
    private long eventosTotales;
    private List<AdminCategoriaResponse> categorias;
}
