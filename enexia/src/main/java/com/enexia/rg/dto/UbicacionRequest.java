package com.enexia.rg.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Domicilio, reutilizado por el domicilio fiscal de una organizacion (RF-7.2)
 * y por el lugar de un evento (RF-2.1).
 *
 * La ciudad viaja como ID y no como texto: es lo que permite el filtro en
 * cascada de RF-4.3 (provincia -> ciudad). Si fuera texto libre, "Ushuaia",
 * "ushuaia" y "Ushuaia " serian tres ciudades distintas y el filtro no agruparia
 * nada.
 *
 * Reglas de calle/numero/coordenadas ampliadas 2026-09-10 a pedido del
 * formulario de creacion/edicion de evento (RF-2.1).
 */
@Getter
@Setter
@NoArgsConstructor
public class UbicacionRequest {

    @NotBlank(message = "La calle es obligatoria")
    @Size(min = 5, max = 120, message = "La calle debe tener entre 5 y 120 caracteres")
    private String calle;

    /**
     * Numerico y positivo: "0" o un texto con letras no es una altura valida.
     * Viaja como String (no Integer) para no perder ceros a la izquierda si
     * alguna vez hiciera falta, pero el patron solo admite digitos.
     */
    @NotBlank(message = "El numero es obligatorio")
    @Pattern(regexp = "^[1-9][0-9]{0,4}$",
            message = "El numero debe ser positivo, numerico y de hasta 5 digitos")
    private String numeroExterior;

    /** Piso, departamento u oficina. Opcional; si viene, sin simbolos. */
    @Size(min = 1, max = 12, message = "El numero interior debe tener entre 1 y 12 caracteres")
    @Pattern(regexp = "^[A-Za-z0-9 ]*$",
            message = "El numero interior no admite caracteres especiales")
    private String numeroInterior;

    @NotNull(message = "La ciudad es obligatoria")
    private Long idCiudad;

    /**
     * Coordenadas opcionales.
     *
     * RF-4.4 pide exponer latitud/longitud en la ficha publica, pero exigirlas en
     * la carga obligaria al organizador a buscarlas a mano. Se aceptan si vienen
     * (por ejemplo, de un selector de mapa) y se dejan nulas si no.
     */
    @DecimalMin(value = "-90", message = "La latitud debe estar entre -90 y 90")
    @DecimalMax(value = "90", message = "La latitud debe estar entre -90 y 90")
    private BigDecimal latitud;

    @DecimalMin(value = "-180", message = "La longitud debe estar entre -180 y 180")
    @DecimalMax(value = "180", message = "La longitud debe estar entre -180 y 180")
    private BigDecimal longitud;
}
