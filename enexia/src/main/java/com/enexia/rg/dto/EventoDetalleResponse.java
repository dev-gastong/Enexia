package com.enexia.rg.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ficha tecnica completa de un evento (RF-4.4).
 *
 * Reune lo que el DFD 4.4 manda componer: descripcion enriquecida, direccion
 * exacta resuelta con coordenadas, agenda de cronogramas con horarios, y precios
 * por tipo de ticket. Todo en UNA respuesta: obligar al frontend a encadenar
 * cuatro peticiones para pintar una sola pantalla multiplicaria la latencia y
 * dejaria la ficha a medio dibujar si una fallara.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoDetalleResponse {

    private Long idEvento;
    private String nombre;
    private String descripcion;
    private String categoria;
    private Long idCategoria;
    private String urlPortada;

    /** Firma ya resuelta segun RF-7.4. */
    private String organizador;

    /** true si el evento se publica a nombre de una organizacion. */
    private boolean organizadorEsOrganizacion;

    private String estadoSistema;
    private String estadoOrganizador;

    // --- Direccion resuelta (RF-4.4)
    private String calle;
    private String numero;
    private String numeroInterior;
    private String ciudad;
    private Long idCiudad;
    private String provincia;
    private String pais;
    private BigDecimal latitud;
    private BigDecimal longitud;

    private List<String> imagenes;
    private List<EventoCronogramaResponse> cronogramas;

    private LocalDateTime fechaCreacion;
}
