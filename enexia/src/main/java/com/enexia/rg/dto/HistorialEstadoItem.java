package com.enexia.rg.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Un cambio de estado en la linea de tiempo de una inscripcion (RF-3.6, DFD 3.6.4). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistorialEstadoItem {

    private String estado;
    private LocalDateTime fechaCambio;
}
