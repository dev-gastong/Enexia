package com.enexia.rg.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Credenciales de acceso (DFD Login 1.2.3 "Validar Formatos de Texto").
 *
 * Las restricciones son deliberadamente laxas comparadas con las del registro:
 * exigir aca el patron de contrasena fuerte le revelaria a un atacante que su
 * candidato no cumple la politica, y ademas rompe el login de cuentas creadas
 * antes de un eventual endurecimiento de la politica.
 */
@Getter
@Setter
@NoArgsConstructor
public class UsuarioLoginRequest {

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El formato del email no es valido")
    @Size(max = 150, message = "El email no puede superar los 150 caracteres")
    private String email;

    @NotBlank(message = "La contrasena es obligatoria")
    @Size(max = 72, message = "La contrasena no puede superar los 72 caracteres")
    private String password;
}
