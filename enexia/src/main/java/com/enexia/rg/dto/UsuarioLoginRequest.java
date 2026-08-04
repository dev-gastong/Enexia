package com.enexia.rg.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UsuarioLoginRequest {
    private String email;
    private String password;
}
