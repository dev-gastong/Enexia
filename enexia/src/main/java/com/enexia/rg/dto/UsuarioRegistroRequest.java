package com.enexia.rg.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UsuarioRegistroRequest {
    private String email;
    private String password;
    private String passwordConfirmacion;
    private String nombre;
    private String apellido;
    private String dni;
}
