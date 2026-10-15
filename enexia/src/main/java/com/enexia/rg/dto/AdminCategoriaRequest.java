package com.enexia.rg.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Alta y renombre de una categoria (RF-6.3). */
@Getter
@Setter
@NoArgsConstructor
public class AdminCategoriaRequest {

    @NotBlank(message = "El nombre de la categoria es obligatorio")
    @Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres")
    private String nombre;
}
