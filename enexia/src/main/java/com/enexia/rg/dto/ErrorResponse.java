package com.enexia.rg.dto;

import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cuerpo unico de todas las respuestas de error de la API.
 *
 * Que el formato sea siempre el mismo le permite al frontend escribir un solo
 * manejador de errores en lugar de uno por endpoint.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /** Etiqueta corta y estable, pensada para que el frontend haga switch sobre ella. */
    private String error;

    /** Texto en espanol listo para mostrarle al usuario. */
    private String mensaje;

    private int status;

    private LocalDateTime timestamp;

    /**
     * Detalle campo -> motivo, solo presente en errores de validacion.
     * {@code @JsonInclude(NON_NULL)} lo omite del JSON cuando es null.
     */
    private Map<String, String> errores;

    public ErrorResponse(String error, String mensaje, int status) {
        this(error, mensaje, status, LocalDateTime.now(), null);
    }
}
