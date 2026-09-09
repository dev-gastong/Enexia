package com.enexia.rg.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Metricas de rendimiento de un evento (RF-2.10).
 *
 * Se exponen las visitas UNICAS y las TOTALES por separado a proposito: las
 * unicas miden alcance real (cuanta gente distinta lo vio) y las totales miden
 * interes (cuantas veces volvieron a mirarlo). Publicar una sola de las dos
 * invita a leerla como si fuera la otra.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoEstadisticasResponse {

    private Long idEvento;
    private String nombre;

    /** COUNT DISTINCT de usuarios identificados (RF-4.5). Excluye anonimos. */
    private long visitasUnicas;

    /** Todas las cargas de la ficha, anonimas incluidas. */
    private long visitasTotales;

    private long cantidadCronogramas;
    private long cupoTotalOfrecido;
    private long cupoOcupado;
}
