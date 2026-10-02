package com.enexia.rg.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cuerpo del PATCH de moderacion administrativa (RF-6.1).
 *
 * {@code motivoCodigo} es obligatorio solo para una suspension disciplinaria
 * (rechazar un evento que ya estaba aprobado); el service es quien decide
 * eso segun el estado actual del evento, porque depende de un dato que esta
 * en la base y no en el request. Aca solo se valida la forma, nunca con que
 * valor concreto del enum {@code MotivoSuspensionAdmin} tiene que corresponder.
 */
@Getter
@Setter
@NoArgsConstructor
public class AdminDecisionEventoRequest {

    @NotBlank(message = "La decision es obligatoria")
    @Pattern(regexp = "^(APROBAR|RECHAZAR)$", message = "La decision debe ser APROBAR o RECHAZAR")
    private String decision;

    private String motivoCodigo;
}
