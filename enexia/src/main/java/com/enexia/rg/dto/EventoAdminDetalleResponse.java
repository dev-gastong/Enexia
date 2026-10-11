package com.enexia.rg.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Detalle de un evento para el panel de administracion (RF-6.1).
 *
 * Es una vista aparte de {@link EventoResponse} y de la ficha publica
 * ({@link EventoDetalleResponse}) a proposito: la ficha publica registra una
 * visita y solo existe para eventos visibles, y el administrador necesita
 * justamente lo contrario -- abrir un evento rechazado o suspendido sin dejar
 * rastro en las estadisticas del organizador.
 *
 * Solo lleva lo que el administrador necesita para decidir: estado, motivo del
 * rechazo automatico, datos de contexto (categoria, cupo, ubicacion,
 * organizador) y, si el evento esta suspendido, la observacion con la que se
 * suspendio.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoAdminDetalleResponse {

    private Long idEvento;
    private String nombre;
    private String urlPortada;
    private String descripcion;
    private String categoria;

    /** Firma ya resuelta segun RF-7.4 (fantasia, razon social o "Nombre Apellido"). */
    private String organizador;
    /** true si el evento va a nombre de una Persona_Juridica. */
    private Boolean organizadorEsOrganizacion;
    /** Nickname de la cuenta que lo creo; siempre existe aunque la firma sea la de una organizacion. */
    private String organizadorNickname;

    private String estadoSistema;
    /** Motivo del rechazo automatico o de la suspension, segun el estado. */
    private String motivoCodigo;
    private String estadoOrganizador;

    /** Texto libre que dejo el administrador al suspender. Null si no corresponde. */
    private String observacionAdmin;
    /** Cuando se asento la ultima decision del historial; null si nunca hubo una. */
    private LocalDateTime fechaUltimaDecision;

    private Long cupoMaximo;
    private String ciudad;
    private String provincia;
    private LocalDateTime fechaCreacion;
}
