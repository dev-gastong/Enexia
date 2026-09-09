package com.enexia.rg.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.enexia.rg.dto.OrganizacionRegistroRequest;
import com.enexia.rg.dto.OrganizacionResponse;
import com.enexia.rg.service.PersonaJuridicaService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Alta y consulta de organizaciones para un usuario ya autenticado (RF-7.2).
 *
 * ES EL CAMINO QUE DESCRIBE RF-7.2 AL PIE DE LA LETRA: "Una Persona Fisica con
 * rol ORGANIZADOR puede crear una Persona Juridica como contenedor
 * administrativo a traves de un flujo separado (no como parte del registro
 * inicial)". El otro camino, el del DFD 7.1/7.2 (bifurcacion dentro del
 * formulario de registro), vive en AuthController y delega en el mismo servicio.
 *
 * La ruta cuelga de {@code /api/organizador/**}, que SecurityConfig ya exige
 * {@code hasRole("ORGANIZADOR")}. El {@code @PreAuthorize} de abajo es
 * redundante a proposito: si manana alguien reorganiza las rutas y esta deja de
 * caer bajo ese prefijo, la anotacion sigue protegiendo el endpoint. Defensa en
 * profundidad sobre un endpoint que crea entidades legales.
 */
@RestController
@RequestMapping("/api/organizador/organizaciones")
@RequiredArgsConstructor
public class OrganizacionController {

    private final PersonaJuridicaService personaJuridicaService;

    /**
     * Registra una organizacion y deja al solicitante como su ADMINISTRADOR.
     *
     * @return 201 Created: la peticion crea un recurso nuevo
     */
    @PostMapping
    @PreAuthorize("hasRole('ORGANIZADOR')")
    public ResponseEntity<OrganizacionResponse> registrar(
            @Valid @RequestBody OrganizacionRegistroRequest peticion,
            Principal principal,
            HttpServletRequest request) {

        OrganizacionResponse respuesta = personaJuridicaService
                .crearOrganizacionPara(principal.getName(), peticion, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    /**
     * Organizaciones donde el usuario figura como miembro.
     *
     * Alimenta el selector "publicar como..." del formulario de creacion de
     * evento (RF-2.1). Devuelve tambien las que estan en revision, con su estado
     * a la vista: es informacion que el organizador necesita para entender por
     * que todavia no puede elegirlas.
     */
    @GetMapping
    @PreAuthorize("hasRole('ORGANIZADOR')")
    public ResponseEntity<List<OrganizacionResponse>> listar(Principal principal) {
        return ResponseEntity.ok(personaJuridicaService.listarDeUsuario(principal.getName()));
    }
}
