package com.enexia.rg.dto;

import java.time.LocalDate;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class InscripcionResponse {
    private Long idInscripcion;
    private String eventoNombre;
    private String estado;
    private LocalDate fechaInscripcion;
    private String precioAbonado;
}
