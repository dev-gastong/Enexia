package com.enexia.rg.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Vista de "Mi Perfil" para el usuario autenticado (Modulo 1).
 *
 * Distinta de {@link UsuarioLoginResponse}: esa devuelve lo minimo para
 * operar el cliente despues de loguear (token, roles). Esta trae ademas la
 * identidad civil (Persona_Fisica) para la pantalla de configuracion de
 * perfil. El DNI viaja de solo lectura -- es un documento de identidad, no
 * un dato que el usuario edite desde la web.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerfilResponse {

    private Long idUsuario;
    private String email;
    private String nickname;
    private List<String> roles;

    private String nombre;
    private String apellido;
    private String dni;
    private LocalDate fechaNacimiento;

    private LocalDateTime fechaRegistro;
}
