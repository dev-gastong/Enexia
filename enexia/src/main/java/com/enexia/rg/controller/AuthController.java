package com.enexia.rg.controller;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.enexia.rg.dto.UsuarioLoginRequest;
import com.enexia.rg.dto.UsuarioLoginResponse;
import com.enexia.rg.dto.UsuarioRegistroRequest;
import com.enexia.rg.dto.UsuarioRegistroResponse;
import com.enexia.rg.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Endpoints publicos de autenticacion (RF-1.1, RF-1.2).
 *
 * El controller es deliberadamente delgado: recibe, delega y traduce a HTTP.
 * No tiene logica de negocio ni un solo try/catch, porque de las excepciones se
 * ocupa GlobalExceptionHandler. Esa separacion es la que permite testear las
 * reglas de autenticacion sin levantar un servidor web.
 *
 * Estas dos rutas estan declaradas como {@code permitAll()} en SecurityConfig:
 * son la puerta de entrada y no pueden exigir el token que todavia no existe.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Alta de cuenta de Persona Fisica.
     *
     * {@code @Valid} dispara las validaciones del DTO ANTES de entrar al metodo.
     * Si alguna falla, Spring lanza MethodArgumentNotValidException, el handler
     * global la convierte en un 400 con el detalle por campo, y este cuerpo
     * nunca llega a ejecutarse.
     *
     * {@code HttpServletRequest} se recibe para poder auditar IP y user-agent.
     * Spring lo inyecta solo con agregarlo a la firma.
     *
     * @return 201 Created, el codigo correcto cuando la peticion crea un recurso
     */
    @PostMapping("/registro")
    public ResponseEntity<UsuarioRegistroResponse> registrar(
            @Valid @RequestBody UsuarioRegistroRequest peticion,
            HttpServletRequest request) {

        UsuarioRegistroResponse respuesta = authService.registrar(peticion, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    /**
     * Inicio de sesion. Devuelve 200 con el JWT.
     *
     * Es POST y no GET aunque "solo consulte": las credenciales viajan en el
     * cuerpo, no en la URL. En un GET irian en la query string, que queda
     * registrada en los logs del servidor, en el historial del navegador y en
     * la cabecera Referer de la siguiente peticion.
     */
    @PostMapping("/login")
    public ResponseEntity<UsuarioLoginResponse> login(
            @Valid @RequestBody UsuarioLoginRequest peticion,
            HttpServletRequest request) {

        return ResponseEntity.ok(authService.login(peticion, request));
    }

    /**
     * Endpoint autenticado que devuelve los datos del usuario del token.
     *
     * Spring inyecta {@code Principal} automaticamente; contiene el usuario
     * autenticado por el filtro JWT. Si no hay sesion valida, el filtro rechaza
     * antes de que entre aqui.
     */
    @GetMapping("/me")
    public ResponseEntity<UsuarioLoginResponse> obtenerDatosActual(Principal principal) {
        return ResponseEntity.ok(authService.obtenerDatosActual(principal.getName()));
    }
}
