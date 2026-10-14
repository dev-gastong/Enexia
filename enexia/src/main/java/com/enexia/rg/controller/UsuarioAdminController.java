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

import com.enexia.rg.dto.AdminCambioEstadoUsuarioRequest;
import com.enexia.rg.dto.AdminUsuarioResponse;
import com.enexia.rg.dto.AdminUsuarioResumenResponse;
import com.enexia.rg.dto.MensajeResponse;
import com.enexia.rg.service.AdminUsuarioService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Panel del administrador - gestion de cuentas de usuario (Modulo 6, RF-6.2).
 *
 * Cuelga de {@code /api/admin/**} (SecurityConfig exige ADMINISTRADOR); el
 * {@code @PreAuthorize} es la segunda capa deliberada, igual que en
 * EventoAdminController.
 */
@RestController
@RequestMapping("/api/admin/usuarios")
@RequiredArgsConstructor
public class UsuarioAdminController {

    private static final int TAMANO_PAGINA_DEFECTO = 10;

    private final AdminUsuarioService adminUsuarioService;

    /** Directorio paginado; {@code estado} filtra por ACTIVO, SUSPENDIDO, BLOQUEADO o DE_BAJA. */
    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Page<AdminUsuarioResponse>> listar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "" + TAMANO_PAGINA_DEFECTO) int tamano,
            Principal principal) {

        PageRequest paginado = PageRequest.of(
                Math.max(0, pagina),
                Math.min(Math.max(1, tamano), AdminUsuarioService.TAMANO_PAGINA_MAXIMO),
                Sort.by(Sort.Direction.DESC, "idUsuario"));

        return ResponseEntity.ok(
                adminUsuarioService.listar(principal.getName(), texto, estado, paginado));
    }

    /** Totales de las tarjetas del panel. */
    @GetMapping("/resumen")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<AdminUsuarioResumenResponse> resumen() {
        return ResponseEntity.ok(adminUsuarioService.resumen());
    }

    /**
     * Suspende o reactiva una cuenta. Responde con el mensaje que el panel
     * muestra en el modal de confirmacion: la confirmacion solo aparece si el
     * servidor efectivamente aplico el cambio.
     */
    @PatchMapping("/{idUsuario}/estado")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<MensajeResponse> cambiarEstado(
            @PathVariable Long idUsuario,
            @Valid @RequestBody AdminCambioEstadoUsuarioRequest datos,
            Principal principal,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                adminUsuarioService.cambiarEstado(principal.getName(), idUsuario, datos, request));
    }
}
