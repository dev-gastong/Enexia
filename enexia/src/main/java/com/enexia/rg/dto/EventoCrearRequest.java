package com.enexia.rg.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Formulario completo de creacion de evento (RF-2.1 a RF-2.6, DFD 2.0-2.4).
 *
 * VIAJA COMO UNA PARTE JSON DE UNA PETICION MULTIPART
 * RF-2.2 dice que "el organizador carga el formulario completo en una unica
 * peticion", imagenes incluidas. Por eso el endpoint es multipart: esta clase es
 * la parte {@code datos} y las imagenes van en la parte {@code imagenes}.
 * La alternativa (imagenes en Base64 dentro del JSON) infla el cuerpo un 33% y
 * obliga a tener el archivo entero en memoria dos veces.
 *
 * OJO CON EL CICLO DE VIDA DE ESTOS DATOS
 * Nada de lo que hay aca se persiste al recibirlo. El paso 2.4 del DFD guarda un
 * "skeleton" con solo cuatro metadatos, y este contenido queda EN MEMORIA hasta
 * que la moderacion asincrona lo apruebe (RF-2.2). Es la regla arquitectonica
 * del proyecto: moderar antes de persistir contenido.
 */
@Getter
@Setter
@NoArgsConstructor
public class EventoCrearRequest {

    @NotBlank(message = "El nombre del evento es obligatorio")
    @Size(min = 5, max = 150, message = "El nombre debe tener entre 5 y 150 caracteres")
    private String nombre;

    @NotBlank(message = "La descripcion es obligatoria")
    @Size(min = 20, max = 5000, message = "La descripcion debe tener entre 20 y 5000 caracteres")
    private String descripcion;

    @NotNull(message = "La categoria es obligatoria")
    private Long idCategoria;

    /**
     * Organizacion bajo la que se publica (RF-2.1 Sprint 2). Opcional.
     *
     * Null = el evento va a titulo personal, firmado con el nombre y apellido
     * del organizador (RF-7.4). Con valor, el service verifica que el usuario
     * sea miembro de esa organizacion y que este habilitada.
     */
    private Long idPersonaJuridica;

    @NotNull(message = "La ubicacion del evento es obligatoria")
    @Valid
    private UbicacionRequest ubicacion;

    /**
     * Agenda del evento (RF-2.4). Al menos una fecha.
     *
     * El tope de 20 no es arbitrario: cada fecha arrastra sus tickets, y sin
     * limite una sola peticion podria generar cientos de filas y volver
     * imposible de leer la ficha publica.
     */
    @NotEmpty(message = "El evento debe tener al menos una fecha en la agenda")
    @Size(max = 20, message = "No se admiten mas de 20 fechas por evento")
    @Valid
    private List<CronogramaRequest> cronogramas;
}
