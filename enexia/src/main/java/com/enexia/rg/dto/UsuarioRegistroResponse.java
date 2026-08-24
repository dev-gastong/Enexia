package com.enexia.rg.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Respuesta del alta de cuenta (DFD Registro, salida "Registro Exitoso").
 *
 * Devuelve lo minimo para que el frontend confirme y redirija. No incluye token:
 * el registro no autentica, el usuario debe pasar por /login. Tampoco expone el
 * hash de la contrasena ni la entidad Usuario.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioRegistroResponse {
    private Long idUsuario;
    private String email;
    private String nickname;
    private String estado;
    private List<String> roles;
    private String mensaje;
}
