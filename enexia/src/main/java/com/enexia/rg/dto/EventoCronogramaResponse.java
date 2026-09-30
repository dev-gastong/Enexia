package com.enexia.rg.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Una fecha de la agenda con sus tickets (RF-2.4, RF-4.4). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoCronogramaResponse {

    private Long idCronograma;
    private LocalDate fecha;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private List<TicketResponse> tickets;

    /** fecha + horaFin ya paso: no se puede inscribir mas (RF-3.1). */
    private boolean finalizado;

    /**
     * El visitante autenticado ya tiene una inscripcion activa para ESTE
     * cronograma (ver InscripcionRepository.existeActivaDeUsuarioEnCronograma).
     * Siempre {@code false} para un visitante anonimo.
     */
    private boolean yaInscripto;

    /**
     * El visitante autenticado puede dejar una valoracion para ESTE
     * cronograma ahora mismo (RF-3.4): tuvo una inscripcion CONFIRMADA, el
     * cronograma ya finalizo, y todavia no lo valoro. Las tres condiciones
     * son las que igual rechazaria {@code ValoracionService.crear}; resolverlo
     * aca es lo que le permite al frontend no ofrecer el formulario donde se
     * sabe de antemano que el POST va a fallar -- mismo criterio que
     * "finalizado"/"yaInscripto" para el boton de inscribirse. Siempre
     * {@code false} para un visitante anonimo.
     */
    private boolean puedeValorar;
}
