package com.enexia.rg.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Una categoria con sus conteos, para las tarjetas del ABM (RF-6.3). */
@Getter
@AllArgsConstructor
public class AdminCategoriaResponse {

    private Long idCategoria;
    private String nombre;
    private boolean activa;

    /** Eventos hoy visibles en el catalogo publico bajo esta categoria. */
    private long eventosPublicados;

    /** Todos los eventos de la categoria que no fueron dados de baja. */
    private long eventosTotales;
}
