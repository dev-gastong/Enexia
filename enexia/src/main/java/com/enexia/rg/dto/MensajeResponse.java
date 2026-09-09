package com.enexia.rg.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Respuesta minima para operaciones que no devuelven un recurso.
 *
 * Existe para no responder cuerpos vacios ni Strings sueltos: el frontend
 * siempre recibe JSON con la misma forma, tanto en exito como en error.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MensajeResponse {

    private String mensaje;
}
