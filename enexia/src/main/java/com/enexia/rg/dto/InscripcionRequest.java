package com.enexia.rg.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class InscripcionRequest {
    private Long idCronogramaTicket;
    private Long idUsuario;
}
