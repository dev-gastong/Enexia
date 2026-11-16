package com.enexia.rg.dto;

/**
 * Resumen del plan y uso de eventos para el organizador autenticado.
 */
public record OrganizadorPlanResumenResponse(
        String tipoPlan,
        long eventosActivos,
        Integer limiteEventos,
        boolean puedeCrear
) {}

