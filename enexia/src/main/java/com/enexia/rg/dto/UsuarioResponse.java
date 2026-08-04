package com.enexia.rg.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UsuarioResponse {
    private Long idUsuario;
    private String email;
    private String nombre;
    private String apellido;
    private String estado;
    private String rol;
}
