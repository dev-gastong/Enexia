package com.enexia.rg.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.enexia.rg.dto.MiembroAltaRequest;
import com.enexia.rg.dto.MiembroResponse;
import com.enexia.rg.service.MiembrosOrganizacionService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * "Mi Equipo": listado y gestion de miembros de una organizacion.
 *
 * Cuelga de {@code /api/organizador/organizaciones/{idPersonaJuridica}/miembros},
 * bajo el mismo prefijo que {@link OrganizacionController}. La autorizacion de
 * grano fino (¿es esta organizacion tuya? ¿sos ADMINISTRADOR de ella?) la
 * resuelve {@link MiembrosOrganizacionService}, no este controller: el
 * {@code @PreAuthorize} de aca solo repite la defensa de rol que
 * {@code SecurityConfig} ya aplica a todo {@code /api/organizador/**}.
 */
@RestController
@RequestMapping("/api/organizador/organizaciones/{idPersonaJuridica}/miembros")
@RequiredArgsConstructor
public class MiembrosOrganizacionController {

    private final MiembrosOrganizacionService miembrosService;

    /** Cualquier miembro de la organizacion puede ver el equipo completo. */
    @GetMapping
    @PreAuthorize("hasRole('ORGANIZADOR')")
    public ResponseEntity<List<MiembroResponse>> listar(
            @PathVariable Long idPersonaJuridica, Principal principal) {

        return ResponseEntity.ok(miembrosService.listar(principal.getName(), idPersonaJuridica));
    }

    /**
     * Agrega a un usuario existente como miembro. Solo un ADMINISTRADOR de la
     * organizacion puede hacerlo (verificado en el service).
     *
     * @return 201 Created: la peticion crea una fila nueva en miembros_organizacion
     */
    @PostMapping
    @PreAuthorize("hasRole('ORGANIZADOR')")
    public ResponseEntity<MiembroResponse> agregar(
            @PathVariable Long idPersonaJuridica,
            @Valid @RequestBody MiembroAltaRequest peticion,
            Principal principal,
            HttpServletRequest request) {

        MiembroResponse respuesta = miembrosService.agregar(
                principal.getName(), idPersonaJuridica, peticion, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    /**
     * Quita a un miembro. Solo un ADMINISTRADOR de la organizacion puede
     * hacerlo, y nunca al ultimo ADMINISTRADOR que quede (verificado en el
     * service).
     *
     * @return 204 No Content: no hay nada que devolver tras un borrado exitoso
     */
    @DeleteMapping("/{idUsuario}")
    @PreAuthorize("hasRole('ORGANIZADOR')")
    public ResponseEntity<Void> quitar(
            @PathVariable Long idPersonaJuridica,
            @PathVariable Long idUsuario,
            Principal principal,
            HttpServletRequest request) {

        miembrosService.quitar(principal.getName(), idPersonaJuridica, idUsuario, request);
        return ResponseEntity.noContent().build();
    }
}
