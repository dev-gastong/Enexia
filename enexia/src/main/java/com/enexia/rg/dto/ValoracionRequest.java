package com.enexia.rg.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Calificacion de un cronograma ya finalizado (RF-3.4, RF-3.5). */
@Getter
@Setter
@NoArgsConstructor
public class ValoracionRequest {

    @NotNull(message = "Hay que indicar el cronograma a valorar")
    private Long idCronograma;

    @NotNull(message = "El puntaje es obligatorio")
    @Min(value = 1, message = "El puntaje minimo es 1")
    @Max(value = 5, message = "El puntaje maximo es 5")
    private Integer valor;

    /** Opcional: la moderacion sincrona (RF-3.5) solo corre si viene texto. */
    @Size(max = 1000, message = "El comentario no puede superar los 1000 caracteres")
    private String comentario;
}
