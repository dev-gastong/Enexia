package com.enexia.rg.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Datos editables de "Mi Perfil" (Modulo 1).
 *
 * Deliberadamente acotado a nombre, apellido y fecha de nacimiento. Email y
 * nickname identifican la cuenta (login, unicidad) y cambiarlos desde aca
 * necesitaria su propio flujo de verificacion; el DNI identifica a la
 * persona y no tiene sentido de negocio que el usuario lo autoedite.
 *
 * nombre/apellido usan una UNICA anotacion a proposito, con el mismo
 * lookahead que TicketRequest.tipoTicket: Bean Validation no garantiza el
 * orden de evaluacion entre varias anotaciones del mismo campo (sin grupos,
 * que este proyecto no adopta), y con @NotBlank + @Size + @Pattern por
 * separado un valor vacio termino mostrando "debe tener entre 2 y 50
 * caracteres" en lugar de "es obligatorio" -- el mismo bug ya documentado en
 * docs/log/sprint_2/2026-09-10_editar_evento.md, reproducido aca al copiar
 * las reglas de UsuarioRegistroRequest. El lookahead exige 2 a 50 caracteres
 * en total; el resto exige el alfabeto permitido, y de paso cubre el string
 * vacio con un solo mensaje.
 */
@Getter
@Setter
@NoArgsConstructor
public class PerfilActualizarRequest {

    @Pattern(regexp = "^(?=.{2,50}$)[\\p{L} '-]+$",
            message = "El nombre es obligatorio: entre 2 y 50 caracteres, solo letras, espacios, apostrofes y guiones")
    private String nombre;

    @Pattern(regexp = "^(?=.{2,50}$)[\\p{L} '-]+$",
            message = "El apellido es obligatorio: entre 2 y 50 caracteres, solo letras, espacios, apostrofes y guiones")
    private String apellido;

    @NotNull(message = "La fecha de nacimiento es obligatoria")
    @Past(message = "La fecha de nacimiento debe ser anterior a hoy")
    private LocalDate fechaNacimiento;
}
