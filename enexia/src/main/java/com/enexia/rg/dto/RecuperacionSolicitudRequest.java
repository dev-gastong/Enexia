package com.enexia.rg.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Pedido de enlace de recuperacion (RF-1.5, paso 1).
 *
 * Solo el email: pedir cualquier otro dato ("tu DNI para verificar") convertiria
 * el endpoint en un verificador de datos personales para quien ya tenga una
 * lista de correos filtrada.
 */
@Getter
@Setter
@NoArgsConstructor
public class RecuperacionSolicitudRequest {

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El formato del email no es valido")
    @Size(max = 150, message = "El email no puede superar los 150 caracteres")
    private String email;
}
