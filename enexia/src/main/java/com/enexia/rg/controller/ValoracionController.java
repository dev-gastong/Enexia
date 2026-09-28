package com.enexia.rg.controller;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.enexia.rg.dto.ValoracionRequest;
import com.enexia.rg.dto.ValoracionResponse;
import com.enexia.rg.service.ValoracionService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Modulo 3: calificacion de cronogramas finalizados (RF-3.4, RF-3.5). */
@RestController
@RequestMapping("/api/participante/valoraciones")
@RequiredArgsConstructor
public class ValoracionController {

    private final ValoracionService valoracionService;

    @PostMapping
    @PreAuthorize("hasRole('PARTICIPANTE')")
    public ResponseEntity<ValoracionResponse> crear(
            @Valid @RequestBody ValoracionRequest datos,
            Principal principal,
            HttpServletRequest request) {

        ValoracionResponse respuesta = valoracionService.crear(principal.getName(), datos, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }
}
