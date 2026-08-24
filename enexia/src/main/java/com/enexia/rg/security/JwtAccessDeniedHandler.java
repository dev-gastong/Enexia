package com.enexia.rg.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.enexia.rg.dto.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * Respuesta ante una peticion AUTENTICADA que no tiene el rol necesario (RF-1.3).
 *
 * Es el complemento de {@link JwtAuthenticationEntryPoint}: aquel cubre "no se
 * quien sos" (401), este cubre "se quien sos pero no alcanza" (403). Por
 * ejemplo, un PARTICIPANTE con token valido intentando entrar a /api/admin/**.
 *
 * El mensaje es deliberadamente escueto: detallar que rol haria falta le
 * describiria a un atacante el mapa de permisos del sistema.
 */
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponse cuerpo = new ErrorResponse(
                "ACCESO_DENEGADO",
                "No tenes permisos para acceder a este recurso.",
                HttpStatus.FORBIDDEN.value());

        objectMapper.writeValue(response.getOutputStream(), cuerpo);
    }
}
