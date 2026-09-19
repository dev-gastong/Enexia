package com.enexia.rg.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Vista de un miembro de una organizacion para la pantalla "Mi Equipo".
 *
 * No expone el DNI ni la fecha de nacimiento: esta pantalla es de gestion de
 * equipo, no una ficha de identidad completa.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MiembroResponse {

    private Long idUsuario;
    private String nombre;
    private String apellido;
    private String email;
    private String nickname;
    /** ADMINISTRADOR o MIEMBRO. */
    private String rolEnEmpresa;
}
