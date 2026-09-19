package com.enexia.rg.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Incorpora a un usuario ya existente como miembro de una organizacion.
 *
 * NO CREA UNA CUENTA. La persona que se agrega tiene que tener ya un Usuario
 * en Enexia (Persona Fisica): el email solo sirve para ubicarla, igual que en
 * el resto del sistema no hay "invitaciones" por correo todavia. Si el email
 * no corresponde a ninguna cuenta activa, el service rechaza el alta.
 */
@Getter
@Setter
@NoArgsConstructor
public class MiembroAltaRequest {

    @NotBlank(message = "El email del miembro es obligatorio")
    @Email(message = "El formato del email no es valido")
    private String email;

    /**
     * ADMINISTRADOR o MIEMBRO. Opcional: si no se manda, el service asigna
     * MIEMBRO por defecto (el rol con menos privilegios).
     */
    @Pattern(regexp = "^(ADMINISTRADOR|MIEMBRO)$",
            message = "El rol en la empresa debe ser ADMINISTRADOR o MIEMBRO")
    private String rolEnEmpresa;
}
