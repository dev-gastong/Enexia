package com.enexia.rg.controller;

import java.security.Principal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.enexia.rg.dto.AdminSuscripcionAccionRequest;
import com.enexia.rg.dto.AdminSuscripcionOperacionResponse;
import com.enexia.rg.dto.AdminSuscripcionesResumenResponse;
import com.enexia.rg.dto.AdminSuscriptorResponse;
import com.enexia.rg.service.AdminSuscripcionService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Panel del administrador - control de suscripciones (Modulo 6, RF-6.4).
 * Por ahora solo lectura; las intervenciones llegan con sus modales.
 */
@RestController
@RequestMapping("/api/admin/suscripciones")
@RequiredArgsConstructor
public class SuscripcionAdminController {

    private static final int TAMANO_PAGINA_DEFECTO = 10;

    private final AdminSuscripcionService adminSuscripcionService;

    /** Organizadores con su plan vigente, paginado, con busqueda por nombre o email. */
    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Page<AdminSuscriptorResponse>> listar(
            @RequestParam(required = false) String texto,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "" + TAMANO_PAGINA_DEFECTO) int tamano) {

        PageRequest paginado = PageRequest.of(
                Math.max(0, pagina),
                Math.min(Math.max(1, tamano), AdminSuscripcionService.TAMANO_PAGINA_MAXIMO),
                Sort.by(Sort.Direction.DESC, "idUsuario"));

        return ResponseEntity.ok(adminSuscripcionService.listar(texto, paginado));
    }

    /**
     * Intervencion administrativa (RF-6.4): revocar, cambiar de plan o renovar.
     * Responde con el mensaje que muestra la confirmacion del panel.
     */
    @PatchMapping("/{idSuscripcion}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<AdminSuscripcionOperacionResponse> intervenir(
            @PathVariable Long idSuscripcion,
            @Valid @RequestBody AdminSuscripcionAccionRequest datos,
            Principal principal, HttpServletRequest request) {
        return ResponseEntity.ok(
                adminSuscripcionService.intervenir(principal.getName(), idSuscripcion, datos, request));
    }

    /** Tarjetas del panel: totales de toda la base y detalle de los dos planes. */
    @GetMapping("/resumen")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<AdminSuscripcionesResumenResponse> resumen() {
        return ResponseEntity.ok(adminSuscripcionService.resumen());
    }
}
