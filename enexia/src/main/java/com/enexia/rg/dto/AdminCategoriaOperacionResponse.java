package com.enexia.rg.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Resultado de una escritura sobre categorias: el mensaje es el que el panel
 * muestra en el modal de confirmacion (solo aparece si el servidor lo aplico),
 * y la categoria ya con su estado final.
 */
@Getter
@AllArgsConstructor
public class AdminCategoriaOperacionResponse {

    private String mensaje;
    private AdminCategoriaResponse categoria;
}
