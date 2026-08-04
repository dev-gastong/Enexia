package com.enexia.rg.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioLoginResponse {
    private Long idUsuario;
    private String email;
    private String token;
    private String tipoToken = "Bearer";
}
