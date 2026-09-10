package com.enexia.rg.controller;

import java.security.Principal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.enexia.rg.dto.PerfilActualizarRequest;
import com.enexia.rg.dto.PerfilResponse;
import com.enexia.rg.service.PerfilService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * "Mi Perfil" del usuario autenticado (Modulo 1).
 *
 * Cuelga de {@code /api/usuario/**}, que no tiene un matcher propio en
 * SecurityConfig: cae en el cierre por defecto {@code anyRequest().authenticated()},
 * que es exactamente lo que corresponde aca -- cualquier rol autenticado
 * (PARTICIPANTE, ORGANIZADOR o ADMINISTRADOR) tiene un perfil propio que
 * consultar y editar, a diferencia de {@code /api/organizador/**}, que es
 * exclusivo de un rol.
 */
@RestController
@RequestMapping("/api/usuario")
@RequiredArgsConstructor
public class UsuarioController {

    private final PerfilService perfilService;

    @GetMapping("/perfil")
    public ResponseEntity<PerfilResponse> obtenerPerfil(Principal principal) {
        return ResponseEntity.ok(perfilService.obtenerPerfil(principal.getName()));
    }

    @PutMapping("/perfil")
    public ResponseEntity<PerfilResponse> actualizarPerfil(
            @Valid @RequestBody PerfilActualizarRequest peticion,
            Principal principal,
            HttpServletRequest request) {

        return ResponseEntity.ok(perfilService.actualizarPerfil(principal.getName(), peticion, request));
    }
}
