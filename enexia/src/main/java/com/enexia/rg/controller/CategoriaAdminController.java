package com.enexia.rg.controller;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.enexia.rg.dto.AdminCategoriaEstadoRequest;
import com.enexia.rg.dto.AdminCategoriaOperacionResponse;
import com.enexia.rg.dto.AdminCategoriaRequest;
import com.enexia.rg.dto.AdminCategoriasListadoResponse;
import com.enexia.rg.service.AdminCategoriaService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Panel del administrador - ABM de categorias de evento (Modulo 6, RF-6.3).
 * Mismo esquema de doble capa que EventoAdminController.
 */
@RestController
@RequestMapping("/api/admin/categorias")
@RequiredArgsConstructor
public class CategoriaAdminController {

    private final AdminCategoriaService adminCategoriaService;

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<AdminCategoriasListadoResponse> listar() {
        return ResponseEntity.ok(adminCategoriaService.listar());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<AdminCategoriaOperacionResponse> crear(
            @Valid @RequestBody AdminCategoriaRequest datos,
            Principal principal, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminCategoriaService.crear(principal.getName(), datos, request));
    }

    @PutMapping("/{idCategoria}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<AdminCategoriaOperacionResponse> renombrar(
            @PathVariable Long idCategoria,
            @Valid @RequestBody AdminCategoriaRequest datos,
            Principal principal, HttpServletRequest request) {
        return ResponseEntity.ok(
                adminCategoriaService.renombrar(principal.getName(), idCategoria, datos, request));
    }

    /** Desactiva o reactiva (baja logica reversible). */
    @PatchMapping("/{idCategoria}/estado")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<AdminCategoriaOperacionResponse> cambiarEstado(
            @PathVariable Long idCategoria,
            @Valid @RequestBody AdminCategoriaEstadoRequest datos,
            Principal principal, HttpServletRequest request) {
        return ResponseEntity.ok(
                adminCategoriaService.cambiarEstado(principal.getName(), idCategoria, datos, request));
    }
}
