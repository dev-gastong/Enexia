package com.enexia.rg.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cuerpo del PATCH de gestion de cuentas (RF-6.2).
 *
 * El cliente pide una ACCION, no un estado destino: de que estado se parte y
 * a cual se llega lo decide el service con el estado vigente en la base (mismo
 * criterio que {@link AdminDecisionEventoRequest}).
 */
@Getter
@Setter
@NoArgsConstructor
public class AdminCambioEstadoUsuarioRequest {

    @NotBlank(message = "La accion es obligatoria")
    @Pattern(regexp = "^(SUSPENDER|REACTIVAR)$", message = "La accion debe ser SUSPENDER o REACTIVAR")
    private String accion;
}
