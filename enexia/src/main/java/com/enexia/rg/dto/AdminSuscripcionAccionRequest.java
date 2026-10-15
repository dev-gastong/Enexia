package com.enexia.rg.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Intervencion administrativa sobre una suscripcion (RF-6.4).
 *
 * El cliente pide una ACCION; el estado de origen y el destino los resuelve el
 * service con lo que hay en la base. {@code tipoPlan} solo aplica a
 * CAMBIAR_PLAN, y {@code motivo} es opcional: sin el, el service registra el
 * motivo estandar de la accion en el historial.
 */
@Getter
@Setter
@NoArgsConstructor
public class AdminSuscripcionAccionRequest {

    @NotBlank(message = "La accion es obligatoria")
    @Pattern(regexp = "^(REVOCAR|CAMBIAR_PLAN|RENOVAR)$",
             message = "La accion debe ser REVOCAR, CAMBIAR_PLAN o RENOVAR")
    private String accion;

    @Pattern(regexp = "^(FREE|PRO)$", message = "El plan debe ser FREE o PRO")
    private String tipoPlan;

    @Size(max = 255, message = "El motivo no puede superar los 255 caracteres")
    private String motivo;
}
