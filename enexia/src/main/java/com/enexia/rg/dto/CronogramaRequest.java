package com.enexia.rg.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una fecha de presentacion del evento con sus tickets (RF-2.4, RF-2.5).
 *
 * POR QUE LA COHERENCIA HORA_INICIO < HORA_FIN NO SE VALIDA ACA
 * Las anotaciones de Bean Validation miran un campo por vez; comparar dos entre
 * si necesita ver el objeto entero. Esa validacion vive en EventoService, junto
 * con la de fechas duplicadas, que ademas necesita ver TODA la lista.
 */
@Getter
@Setter
@NoArgsConstructor
public class CronogramaRequest {

    /**
     * Fecha de la funcion. {@code @Future} implementa el paso 2.2 del DFD
     * ("Fechas validas y futuras"): no tiene sentido publicar un evento cuya
     * fecha ya paso, nadie podria inscribirse.
     */
    @NotNull(message = "La fecha es obligatoria")
    @Future(message = "La fecha debe ser futura")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fecha;

    @NotNull(message = "La hora de inicio es obligatoria")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime horaInicio;

    @NotNull(message = "La hora de fin es obligatoria")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime horaFin;

    /**
     * Tickets de esta fecha (RF-2.5).
     *
     * {@code @Valid} en cascada: sin el, las reglas de TicketRequest no se
     * ejecutan y llegarian precios negativos al service.
     */
    @NotEmpty(message = "Cada fecha debe ofrecer al menos un tipo de ticket")
    @Size(max = 10, message = "No se admiten mas de 10 tipos de ticket por fecha")
    @Valid
    private List<TicketRequest> tickets;
}
