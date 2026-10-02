package com.enexia.rg.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Credenciales de acceso (DFD Login 1.2.1 "Validar Formatos de Entrada").
 *
 * El identificador de login es el NICKNAME, no el email (decision 2026-09-30):
 * el email se mantiene como dato de contacto (recuperacion de cuenta, avisos
 * de seguridad), pero dejo de ser lo que el usuario escribe para entrar.
 *
 * Las restricciones son deliberadamente laxas comparadas con las del registro
 * (ver UsuarioRegistroRequest, que ademas exige @Size(min=3,max=20) y un
 * @Pattern de caracteres permitidos): exigir aca el mismo patron le revelaria
 * a un atacante que su candidato no cumple la politica, y ademas romperia el
 * login de cuentas creadas antes de un eventual endurecimiento de esa politica.
 */
@Getter
@Setter
@NoArgsConstructor
public class UsuarioLoginRequest {

    @NotBlank(message = "El nickname es obligatorio")
    @Size(max = 20, message = "El nickname no puede superar los 20 caracteres")
    private String nickname;

    @NotBlank(message = "La contrasena es obligatoria")
    @Size(max = 72, message = "La contrasena no puede superar los 72 caracteres")
    private String password;
}
