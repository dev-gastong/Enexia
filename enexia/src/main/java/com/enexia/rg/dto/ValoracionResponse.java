package com.enexia.rg.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValoracionResponse {

    private Long idValoracion;
    private Long idCronograma;
    private String eventoNombre;
    private Integer valor;
    private String comentario;
    private LocalDate fecha;
}
