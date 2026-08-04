package com.enexia.rg.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EventoDetalleResponse {
    private Long idEvento;
    private String nombre;
    private String descripcion;
    private String urlPortada;
    private String categoria;
    private String estado;
    private String organizador;
    private String ubicacion;
    private Double latitud;
    private Double longitud;
}
