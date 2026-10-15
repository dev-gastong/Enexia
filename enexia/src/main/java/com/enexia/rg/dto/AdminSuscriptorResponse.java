package com.enexia.rg.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Builder;
import lombok.Getter;

/**
 * Una fila del control de suscripciones del administrador (RF-6.4): un
 * ORGANIZADOR y su plan vigente.
 *
 * Quien nunca contrato nada no tiene fila en {@code suscripcion}: es plan
 * GRATUITO, {@code idSuscripcion} nulo y estado "GRATUITO". El plan vigente de
 * quien si tiene es el de su ULTIMA suscripcion.
 */
@Getter
@Builder
public class AdminSuscriptorResponse {

    private Long idUsuario;
    private String nombreCompleto;
    private String email;

    /** GRATUITO o PRO. */
    private String plan;

    /** Inicio de la cobertura; para un plan gratuito, la fecha de alta de la cuenta. */
    private LocalDate desde;
    private LocalDate hasta;

    /** MENSUAL, ANUAL o null (gratuito). Se deduce de la duracion de la cobertura. */
    private String facturacion;

    private BigDecimal monto;

    /** GRATUITO, PENDIENTE, ACTIVA, VENCIDA o CANCELADA. */
    private String estado;

    /** Nulo para el plan gratuito sin suscripcion. */
    private Long idSuscripcion;
}
