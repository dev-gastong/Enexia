package com.enexia.rg.controller;

import java.security.Principal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.enexia.rg.dto.InscripcionRequest;
import com.enexia.rg.dto.InscripcionResponse;
import com.enexia.rg.service.InscripcionService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Modulo 3: reservas de tickets del participante (RF-3.1, RF-3.2, RF-3.3, RF-3.6).
 *
 * Cuelga de {@code /api/participante/**}, restringido en SecurityConfig a
 * {@code hasRole("PARTICIPANTE")}. Un ORGANIZADOR tambien tiene ese rol (ver
 * "Role Assignment Strategy" en CLAUDE.md), asi que puede inscribirse a
 * eventos de otros sin necesitar una cuenta aparte.
 */
@RestController
@RequestMapping("/api/participante/inscripciones")
@RequiredArgsConstructor
public class InscripcionController {

    private static final int TAMANO_PAGINA_DEFECTO = 10;
    private static final int TAMANO_PAGINA_MAXIMO = 50;

    private final InscripcionService inscripcionService;

    /**
     * Reserva un ticket (RF-3.1). Responde 201 aun cuando el ticket es de
     * pago y la pasarela lo rechaza: la inscripcion SI se crea (queda en
     * PENDIENTE_PAGO), solo que ese caso puntual lo maneja
     * {@code GlobalExceptionHandler} devolviendo 402 en su lugar.
     */
    @PostMapping
    @PreAuthorize("hasRole('PARTICIPANTE')")
    public ResponseEntity<InscripcionResponse> inscribir(
            @Valid @RequestBody InscripcionRequest datos,
            Principal principal,
            HttpServletRequest request) {

        InscripcionResponse respuesta = inscripcionService.inscribir(principal.getName(), datos, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    /** Cancelacion voluntaria (RF-3.3). DELETE por semantica HTTP; internamente es borrado logico. */
    @DeleteMapping("/{idInscripcion}")
    @PreAuthorize("hasRole('PARTICIPANTE')")
    public ResponseEntity<InscripcionResponse> cancelar(
            @PathVariable Long idInscripcion,
            Principal principal,
            HttpServletRequest request) {

        return ResponseEntity.ok(inscripcionService.cancelar(principal.getName(), idInscripcion, request));
    }

    /** Historial personal, paginado y cronologico (RF-3.6). */
    @GetMapping
    @PreAuthorize("hasRole('PARTICIPANTE')")
    public ResponseEntity<Page<InscripcionResponse>> historial(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "" + TAMANO_PAGINA_DEFECTO) int tamano,
            Principal principal) {

        PageRequest paginado = PageRequest.of(
                Math.max(0, pagina),
                Math.min(Math.max(1, tamano), TAMANO_PAGINA_MAXIMO),
                Sort.by(Sort.Direction.DESC, "fechaInscripcion"));

        return ResponseEntity.ok(inscripcionService.historial(principal.getName(), paginado));
    }
}
