package com.enexia.rg.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Confirmacion del restablecimiento con el token del email (RF-1.5, paso 2).
 *
 * La politica de contrasena es la MISMA que la del registro, a proposito: si
 * aca fuera mas laxa, este endpoint seria el atajo para eludirla.
 */
@Getter
@Setter
@NoArgsConstructor
public class RecuperacionConfirmarRequest {

    /**
     * Token que viajo en el enlace del correo.
     *
     * Se valida el largo pero no el contenido: son 32 bytes en Base64 URL-safe
     * sin relleno, o sea 43 caracteres. Un token de largo distinto ni siquiera
     * llega a consultarse contra la base.
     */
    @NotBlank(message = "El token es obligatorio")
    @Size(min = 20, max = 100, message = "El token no tiene un formato valido")
    private String token;

    @NotBlank(message = "La contrasena es obligatoria")
    @Size(min = 8, max = 72, message = "La contrasena debe tener entre 8 y 72 caracteres")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
        message = "La contrasena debe incluir al menos una minuscula, una mayuscula y un numero"
    )
    private String password;

    @NotBlank(message = "Debe repetir la contrasena")
    private String passwordConfirmacion;
}
