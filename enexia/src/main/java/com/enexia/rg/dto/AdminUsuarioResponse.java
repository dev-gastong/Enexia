package com.enexia.rg.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

/**
 * Fila del directorio de usuarios del administrador (RF-6.2).
 *
 * {@code gestionable} es la UNICA fuente de verdad de si el panel puede
 * ofrecer Suspender/Reactivar sobre esta cuenta: el service lo calcula con las
 * mismas reglas que despues aplica {@code cambiarEstado}, asi la pantalla no
 * tiene que replicarlas (y no puede quedar desalineada). Una cuenta de otro
 * administrador o la propia del que consulta nunca es gestionable.
 */
@Getter
@Builder
public class AdminUsuarioResponse {

    private Long idUsuario;
    private String nombreCompleto;
    private String nickname;
    private String email;
    private List<String> roles;
    private LocalDateTime fechaRegistro;
    private long cantidadEventos;

    /** ACTIVO, BLOQUEADO, SUSPENDIDO o DE_BAJA (catalogo usuario_estado). */
    private String estado;

    private boolean gestionable;
}
