package com.enexia.rg.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EventoCronogramaResponse {
    private Long idCronograma;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private Integer cupoDisponible;
    private Integer cupoTotal;
}
