package com.enexia.rg.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Por que un evento propio esta rechazado o suspendido, tal como lo ve su
 * organizador (RF-2.8 / RF-6.1).
 *
 * Es un endpoint aparte y NO un campo mas de {@link EventoResponse} a
 * proposito: esa tarjeta tambien alimenta el catalogo publico, y la
 * observacion que deja el administrador no tiene que viajar jamas por una
 * respuesta que ve cualquier visitante.
 *
 * {@code observacionAdmin} solo viene cargada cuando el evento esta
 * suspendido por un administrador. Un rechazo automatico no tiene texto libre:
 * su motivo es solo {@code motivoCodigo}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoModeracionResponse {

    private Long idEvento;
    private String estadoSistema;
    /** Motivo del rechazo automatico o de la suspension, segun el estado. */
    private String motivoCodigo;
    /** Texto libre del administrador; solo para un evento suspendido. */
    private String observacionAdmin;
    /** Cuando se asento la decision de suspension; null si no corresponde. */
    private LocalDateTime fechaDecision;
}
