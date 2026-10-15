package com.enexia.rg.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Baja logica o reactivacion de una categoria (RF-6.3): el cliente pide una accion, no un estado. */
@Getter
@Setter
@NoArgsConstructor
public class AdminCategoriaEstadoRequest {

    @NotBlank(message = "La accion es obligatoria")
    @Pattern(regexp = "^(DESACTIVAR|ACTIVAR)$", message = "La accion debe ser DESACTIVAR o ACTIVAR")
    private String accion;
}
