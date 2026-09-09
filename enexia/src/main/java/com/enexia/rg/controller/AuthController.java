package com.enexia.rg.controller;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.enexia.rg.dto.MensajeResponse;
import com.enexia.rg.dto.RecuperacionConfirmarRequest;
import com.enexia.rg.dto.RecuperacionSolicitudRequest;
import com.enexia.rg.dto.RegistroConOrganizacionRequest;
import com.enexia.rg.dto.RegistroConOrganizacionResponse;
import com.enexia.rg.dto.UsuarioLoginRequest;
import com.enexia.rg.dto.UsuarioLoginResponse;
import com.enexia.rg.dto.UsuarioRegistroRequest;
import com.enexia.rg.dto.UsuarioRegistroResponse;
import com.enexia.rg.service.AuthService;
import com.enexia.rg.service.RecuperacionCuentaService;

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
    private final RecuperacionCuentaService recuperacionCuentaService;

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
     * Alta en un solo paso: cuenta de Persona Fisica + organizacion (DFD 7.1/7.2).
     *
     * Es la rama "Juridica" de la bifurcacion que el DFD dibuja DENTRO del
     * formulario de registro. Ojo con el nombre: lo que se crea es igual una
     * cuenta personal; la organizacion es un contenedor administrativo que
     * cuelga de ella. No existe un login "de empresa".
     *
     * Publico como el resto del registro. Que la organizacion nazca en
     * REVISION_PENDIENTE es lo que hace seguro dejarlo abierto: cualquiera puede
     * declarar una empresa, pero nadie publica a nombre corporativo hasta que un
     * administrador verifique el CUIT.
     */
    @PostMapping("/registro/organizacion")
    public ResponseEntity<RegistroConOrganizacionResponse> registrarConOrganizacion(
            @Valid @RequestBody RegistroConOrganizacionRequest peticion,
            HttpServletRequest request) {

        RegistroConOrganizacionResponse respuesta =
                authService.registrarConOrganizacion(peticion, request);

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

    // =====================================================================
    // RECUPERACION DE ACCESO  (RF-1.5)
    // =====================================================================

    /**
     * Pide el enlace de restablecimiento.
     *
     * RESPONDE SIEMPRE 202 CON EL MISMO TEXTO, exista o no el email. Es la misma
     * regla que gobierna el login: si contestara 404 para un email desconocido,
     * este endpoint publico seria un enumerador de cuentas todavia mas comodo
     * que el propio login, porque no requiere ni siquiera adivinar contrasenas.
     *
     * 202 Accepted y no 200 OK: el envio del correo es asincrono, asi que en el
     * momento de responder la accion esta aceptada pero no completada.
     */
    @PostMapping("/recuperacion")
    public ResponseEntity<MensajeResponse> solicitarRecuperacion(
            @Valid @RequestBody RecuperacionSolicitudRequest peticion) {

        recuperacionCuentaService.solicitarRestablecimiento(peticion.getEmail());

        return ResponseEntity.accepted().body(new MensajeResponse(
                "Si el email corresponde a una cuenta registrada, te enviamos un enlace "
                + "para restablecer la contrasena."));
    }

    /**
     * Consume el token del correo y fija la contrasena nueva.
     *
     * Si la cuenta estaba BLOQUEADA por intentos fallidos, este es el camino de
     * desbloqueo: el titular demuestra que controla la casilla y recupera el
     * acceso sin depender de un administrador (RF-1.4 + RF-1.5).
     */
    @PostMapping("/recuperacion/confirmar")
    public ResponseEntity<MensajeResponse> confirmarRecuperacion(
            @Valid @RequestBody RecuperacionConfirmarRequest peticion) {

        recuperacionCuentaService.confirmarRestablecimiento(
                peticion.getToken(), peticion.getPassword(), peticion.getPasswordConfirmacion());

        return ResponseEntity.ok(new MensajeResponse(
                "Contrasena actualizada. Ya podes iniciar sesion."));
    }
}
