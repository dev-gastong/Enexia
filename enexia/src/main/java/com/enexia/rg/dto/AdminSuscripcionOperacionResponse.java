package com.enexia.rg.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Resultado de una intervencion sobre una suscripcion: el mensaje es el que el
 * panel muestra en la confirmacion (solo aparece si el servidor la aplico) y el
 * suscriptor ya con su estado final.
 */
@Getter
@AllArgsConstructor
public class AdminSuscripcionOperacionResponse {

    private String mensaje;
    private AdminSuscriptorResponse suscriptor;
}
