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
}
