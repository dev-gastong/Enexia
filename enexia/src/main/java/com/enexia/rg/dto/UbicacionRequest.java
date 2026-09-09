package com.enexia.rg.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
 */
@Getter
@Setter
@NoArgsConstructor
public class UbicacionRequest {

    @NotBlank(message = "La calle es obligatoria")
    @Size(max = 120, message = "La calle no puede superar los 120 caracteres")
    private String calle;

    @NotBlank(message = "El numero es obligatorio")
    @Size(max = 10, message = "El numero no puede superar los 10 caracteres")
    private String numeroExterior;

    /** Piso, departamento u oficina. Opcional. */
    @Size(max = 10, message = "El numero interior no puede superar los 10 caracteres")
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
    private java.math.BigDecimal latitud;

    private java.math.BigDecimal longitud;
}
