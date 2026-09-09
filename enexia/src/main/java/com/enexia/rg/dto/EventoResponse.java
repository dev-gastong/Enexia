package com.enexia.rg.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Vista de tarjeta de un evento: catalogo publico (RF-4.1) y dashboard del
 * organizador (RF-2.8).
 *
 * Deliberadamente plano y sin colecciones: una grilla de 20 tarjetas que
 * arrastrara cronogramas y tickets multiplicaria por diez el JSON y el tiempo de
 * consulta, para mostrar datos que la tarjeta ni siquiera pinta. El detalle
 * completo se pide al abrir la ficha (RF-4.4).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoResponse {

    private Long idEvento;
    private String nombre;
    private String urlPortada;
    private String categoria;

    /**
     * Firma del organizador ya resuelta segun RF-7.4: nombre de fantasia, o
     * razon social si no hay fantasia, o "Nombre Apellido" si el evento va a
     * titulo personal. El frontend no tiene que decidir nada.
     */
    private String organizador;

    /** EN_PROCESO / APROBADO_SISTEMA / RECHAZADO_SISTEMA... (RF-2.2). */
    private String estadoSistema;

    /** Motivo del rechazo automatico, si lo hubo. Null en el resto de los casos. */
    private String motivoRechazo;

    /** PUBLICADO / CANCELADO / DADO_DE_BAJA / FINALIZADO (RF-2.9). */
    private String estadoOrganizador;

    /** Primera fecha futura de la agenda, para ordenar y mostrar en la tarjeta. */
    private String proximaFecha;

    private String ciudad;
    private String provincia;
    private LocalDateTime fechaCreacion;
}
