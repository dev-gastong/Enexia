package com.enexia.rg.controller;

import java.security.Principal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.enexia.rg.dto.AdminDecisionEventoRequest;
import com.enexia.rg.dto.EventoResponse;
import com.enexia.rg.service.AdminEventoService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Panel del administrador - moderacion de eventos (Modulo 6, RF-6.1).
 *
 * Cuelga de {@code /api/admin/**}, que SecurityConfig ya restringe a
 * {@code hasRole("ADMINISTRADOR")}. El {@code @PreAuthorize} es la misma
 * segunda capa deliberada que usa EventoOrganizadorController.
 */
@RestController
@RequestMapping("/api/admin/eventos")
@RequiredArgsConstructor
public class EventoAdminController {

    private static final int TAMANO_PAGINA_DEFECTO = 10;

    private final AdminEventoService adminEventoService;

    /**
     * Cola de moderacion: todos los eventos, de cualquier organizador.
     *
     * Sin filtro de estado en el servidor a proposito: el panel necesita ver
     * a la vez los rechazados por el sistema y los ya aprobados (son los dos
     * escenarios de RF-6.1), asi que el filtro por estado concreto lo aplica
     * el cliente sobre este mismo lote, igual que ya hace mis-eventos.html.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Page<EventoResponse>> listar(
            @RequestParam(required = false) String texto,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "" + TAMANO_PAGINA_DEFECTO) int tamano) {

        PageRequest paginado = PageRequest.of(
                Math.max(0, pagina),
                Math.min(Math.max(1, tamano), AdminEventoService.TAMANO_PAGINA_MAXIMO),
                Sort.by(Sort.Direction.DESC, "fechaCreacion"));

        return ResponseEntity.ok(adminEventoService.listarParaModeracion(texto, paginado));
    }

    /**
     * Decision administrativa sobre un evento (RF-6.1): aprobar de todas
     * formas, ratificar un rechazo automatico, suspender uno ya publicado o
     * revertir una suspension anterior. Cual de las cuatro ocurre lo decide
     * el service segun el estado vigente del evento, no un parametro aparte.
     */
    @PatchMapping("/{idEvento}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<EventoResponse> decidir(
            @PathVariable Long idEvento,
            @Valid @RequestBody AdminDecisionEventoRequest datos,
            Principal principal,
            HttpServletRequest request) {

        return ResponseEntity.ok(
                adminEventoService.decidir(principal.getName(), idEvento, datos, request));
    }
}
