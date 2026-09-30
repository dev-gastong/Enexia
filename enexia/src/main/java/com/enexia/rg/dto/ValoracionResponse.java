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

    /**
     * Nickname del autor, no nombre y apellido civiles.
     *
     * Mismo criterio que {@code EventoMapper.firmaOrganizador} ya aplica para
     * el organizador: no exponer un dato personal (nombre completo de
     * PersonaFisica) que la vista publica no necesita para cumplir su
     * proposito.
     */
    private String autor;

    /** Fecha del cronograma valorado (RF-3.4: cada reseña dice a que función se refiere). */
    private LocalDate cronogramaFecha;
}
