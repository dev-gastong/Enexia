package com.enexia.rg.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.enexia.rg.dto.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * Respuesta ante una peticion SIN autenticar a un endpoint protegido.
 *
 * POR QUE HACE FALTA
 * Sin esta clase, Spring Security responde 403 Forbidden a las peticiones sin
 * token. Es semanticamente incorrecto:
 *
 *   401 Unauthorized -> "no se quien sos, autenticate"
 *   403 Forbidden    -> "se quien sos, pero no tenes permiso"
 *
 * La diferencia le importa al frontend: ante un 401 corresponde mandar al
 * usuario al login o refrescar el token; ante un 403 hay que mostrarle que no
 * tiene permisos, porque reintentar no va a servir de nada.
 *
 * Ademas devuelve el mismo formato ErrorResponse que el resto de la API. Los
 * errores de la cadena de filtros ocurren ANTES de llegar a los controllers,
 * asi que GlobalExceptionHandler no los ve y hay que darles forma aca.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponse cuerpo = new ErrorResponse(
                "NO_AUTENTICADO",
                "Se requiere un token valido para acceder a este recurso.",
                HttpStatus.UNAUTHORIZED.value());

        objectMapper.writeValue(response.getOutputStream(), cuerpo);
    }
}
