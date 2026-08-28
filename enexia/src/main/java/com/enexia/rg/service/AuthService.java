package com.enexia.rg.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.enexia.rg.dto.UsuarioLoginRequest;
import com.enexia.rg.dto.UsuarioLoginResponse;
import com.enexia.rg.dto.UsuarioRegistroRequest;
import com.enexia.rg.dto.UsuarioRegistroResponse;
import com.enexia.rg.exception.ContenidoInapropiadoException;
import com.enexia.rg.exception.CredencialesInvalidasException;
import com.enexia.rg.exception.CuentaBloqueadaException;
import com.enexia.rg.exception.CuentaEnCooldownException;
import com.enexia.rg.exception.RecursoDuplicadoException;
import com.enexia.rg.exception.ReglaNegocioException;
import com.enexia.rg.model.EstadoUsuarioNombre;
import com.enexia.rg.model.Persona;
import com.enexia.rg.model.PersonaFisica;
import com.enexia.rg.model.Rol;
import com.enexia.rg.model.RolNombre;
import com.enexia.rg.model.Usuario;
import com.enexia.rg.model.UsuarioEstado;
import com.enexia.rg.model.UsuarioRol;
import com.enexia.rg.repository.PersonaFisicaRepository;
import com.enexia.rg.repository.PersonaRepository;
import com.enexia.rg.repository.RolRepository;
import com.enexia.rg.repository.UsuarioEstadoRepository;
import com.enexia.rg.repository.UsuarioRepository;
import com.enexia.rg.repository.UsuarioRolRepository;
import com.enexia.rg.security.JwtService;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Logica de registro y autenticacion (RF-1.1, RF-1.2, RF-1.4, RF-1.6).
 *
 * Implementa los DFD de docs/diagrams/login_registro/ con dos exclusiones
 * decididas el 2026-08-24, alineadas con el alcance de Sprint 1 de CLAUDE.md:
 *
 *   - CAPTCHA (paso 1.2.4A): NO se valida. El campo {@code requiere_captcha} SI
 *     se marca al tercer intento fallido, de modo que el dato queda listo y
 *     activar la validacion despues sea agregar un paso, no rehacer la logica.
 *   - 2FA por email (pasos 1.2.8 y 1.2.9): NO se implementa. El login exitoso
 *     emite el JWT directamente.
 *
 * Tampoco entra Persona Juridica (Sprint 2): el registro cubre Persona Fisica.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PersonaRepository personaRepository;
    private final PersonaFisicaRepository personaFisicaRepository;
    private final UsuarioRolRepository usuarioRolRepository;
    private final RolRepository rolRepository;
    private final UsuarioEstadoRepository usuarioEstadoRepository;

    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ModeracionTextoService moderacionService;
    private final AuditoriaService auditoriaService;
    private final RateLimitService rateLimitService;
    private final IntentosLoginService intentosLoginService;

    /**
     * Hash de descarte para nivelar los tiempos de respuesta. Ver
     * {@link #login} para el detalle de por que hace falta.
     */
    private String hashSenuelo;

    @PostConstruct
    void prepararHashSenuelo() {
        // Se calcula una sola vez al arrancar: es un BCrypt real, con el mismo
        // costo que los de la base, asi que comparar contra el tarda lo mismo
        // que comparar contra el hash de un usuario existente.
        this.hashSenuelo = passwordEncoder.encode("senuelo-para-nivelar-tiempos-de-respuesta");
    }

    // =====================================================================
    // LOGIN  (DFD docs/diagrams/login_registro/login.md)
    // =====================================================================

    /**
     * Autentica y emite el JWT.
     *
     * NO lleva {@code @Transactional} a proposito. El camino de fallo necesita
     * que el contador de intentos quede confirmado en la base ANTES de lanzar la
     * excepcion; si todo el metodo fuera una transaccion, esa excepcion la
     * revertiria. Las escrituras las hacen IntentosLoginService y
     * AuditoriaService, cada uno con su propia transaccion REQUIRES_NEW.
     */
    public UsuarioLoginResponse login(UsuarioLoginRequest peticion, HttpServletRequest request) {

        String ip = auditoriaService.ipDe(request);

        // --- Paso 1.2.1: rate limiting por IP.
        // Va primero para que una IP abusiva no llegue a consumir consultas ni
        // ciclos de BCrypt.
        rateLimitService.verificarLimiteLogin(ip);

        // --- Paso 1.2.4: recuperar cuenta y campos de control.
        Usuario usuario = usuarioRepository
                .buscarActivoPorEmailConRoles(peticion.getEmail())
                .orElse(null);

        if (usuario == null) {
            // El email no existe. Se gasta igual el tiempo de un BCrypt real
            // para no responder mas rapido que en el caso "existe pero la clave
            // esta mal": esa diferencia de milisegundos, medida muchas veces,
            // le revela a un atacante que emails estan registrados.
            passwordEncoder.matches(peticion.getPassword(), hashSenuelo);
            auditoriaService.registrarAparte(null, AuditoriaService.ACCION_LOGIN_FALLIDO,
                    "Intento contra un email no registrado", request);
            throw new CredencialesInvalidasException();
        }

        // --- Paso 1.2.4: el estado debe ser ACTIVO (RF-1.6).
        verificarEstadoHabilitado(usuario, request);

        // --- Paso 1.2.4: cooldown vigente.
        verificarCooldown(usuario);

        // --- Paso 1.2.4A (CAPTCHA): fuera de alcance en Sprint 1.
        // El flag requiere_captcha ya se marca en IntentosLoginService; solo
        // falta validar el token contra el proveedor externo.

        // --- Paso 1.2.5: comparar contrasena contra el hash BCrypt.
        if (!passwordEncoder.matches(peticion.getPassword(), usuario.getPassword())) {
            procesarIntentoFallido(usuario, request);
            throw new CredencialesInvalidasException();
        }

        // --- Paso 1.2.5A: credenciales correctas, se limpian los contadores.
        intentosLoginService.limpiarTrasLoginExitoso(usuario.getIdUsuario());

        // --- Pasos 1.2.8 y 1.2.9 (2FA): fuera de alcance en Sprint 1.

        // --- Paso 1.2.10: emitir el JWT con los roles dentro.
        List<String> roles = usuario.getRoles();
        String token = jwtService.generarToken(usuario.getEmail(), roles);

        auditoriaService.registrarAparte(usuario, AuditoriaService.ACCION_LOGIN_EXITOSO,
                "Login correcto", request);
        log.info("Login exitoso para el usuario {}", usuario.getIdUsuario());

        return new UsuarioLoginResponse(
                usuario.getIdUsuario(),
                usuario.getEmail(),
                token,
                "Bearer",
                roles);
    }

    /**
     * Verifica que el estado de la cuenta habilite el ingreso (RF-1.6).
     *
     * BLOQUEADO se distingue con su propio mensaje porque el usuario legitimo
     * necesita saber que tiene que pedir un desbloqueo. SUSPENDIDO y DE_BAJA
     * caen en el mensaje generico: no hay razon para confirmarle a un tercero
     * que esa cuenta existe.
     */
    private void verificarEstadoHabilitado(Usuario usuario, HttpServletRequest request) {
        UsuarioEstado estado = usuario.getEstadoUsuario();
        String nombreEstado = estado != null ? estado.getEstadoUsuario() : null;

        if (EstadoUsuarioNombre.ACTIVO.name().equalsIgnoreCase(nombreEstado)) {
            return;
        }

        auditoriaService.registrarAparte(usuario, AuditoriaService.ACCION_LOGIN_FALLIDO,
                "Intento sobre cuenta en estado " + nombreEstado, request);

        if (EstadoUsuarioNombre.BLOQUEADO.name().equalsIgnoreCase(nombreEstado)) {
            throw new CuentaBloqueadaException();
        }
        throw new CredencialesInvalidasException();
    }

    /** Paso 1.2.4: rechaza si todavia corre la penalizacion temporal. */
    private void verificarCooldown(Usuario usuario) {
        LocalDateTime cooldown = usuario.getFechaDesbloqueoCooldown();
        if (cooldown != null && LocalDateTime.now().isBefore(cooldown)) {
            throw new CuentaEnCooldownException(cooldown);
        }
    }

    /** Pasos 1.2.6 y 1.2.7: contabiliza el fallo, penaliza y audita. */
    private void procesarIntentoFallido(Usuario usuario, HttpServletRequest request) {
        boolean quedoBloqueada = intentosLoginService.registrarFallo(usuario.getIdUsuario());

        auditoriaService.registrarAparte(usuario, AuditoriaService.ACCION_LOGIN_FALLIDO,
                "Contrasena incorrecta", request);

        if (quedoBloqueada) {
            auditoriaService.registrarAparte(usuario, AuditoriaService.ACCION_CUENTA_BLOQUEADA,
                    "Bloqueo automatico por acumulacion de intentos fallidos", request);
            // Paso 1.2.7A (email de alerta de seguridad): pendiente para Sprint 2,
            // cuando se integre el servicio de correo.
            throw new CuentaBloqueadaException();
        }
    }

    // =====================================================================
    // REGISTRO  (DFD docs/diagrams/login_registro/registro.md)
    // =====================================================================

    /**
     * Da de alta una cuenta de Persona Fisica.
     *
     * {@code @Transactional} es obligatorio aca: el alta escribe en cuatro
     * tablas encadenadas (persona -> persona_fisica -> usuario -> usuario_rol).
     * Sin transaccion, un fallo en el tercer paso dejaria una persona sin
     * usuario, ocupando un DNI que despues nadie podria volver a registrar.
     * Con transaccion, o se escriben las cuatro o no se escribe ninguna.
     */
    @Transactional
    public UsuarioRegistroResponse registrar(UsuarioRegistroRequest peticion, HttpServletRequest request) {

        // --- Coherencia entre ambas contrasenas.
        // No lo cubre @Valid: las anotaciones miran un campo por vez y esto
        // compara dos entre si.
        if (!peticion.getPassword().equals(peticion.getPasswordConfirmacion())) {
            throw new ReglaNegocioException("Las contrasenas no coinciden");
        }

        String email = peticion.getEmail().trim().toLowerCase();
        String nickname = peticion.getNickname().trim();

        // --- Paso 1.1.1: unicidad de credenciales.
        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new RecursoDuplicadoException("Ya existe una cuenta registrada con ese email");
        }
        if (usuarioRepository.existsByNicknameIgnoreCase(nickname)) {
            throw new RecursoDuplicadoException("Ese nickname ya esta en uso");
        }

        // --- Paso 1.1.1A: moderacion de texto ANTES de persistir nada.
        // El orden es deliberado: si se guardara primero, un nickname ofensivo
        // quedaria en la base aunque despues se rechace el registro.
        moderarCamposDePerfil(peticion, request);

        // --- Paso 1.1.2 (datos personales): resuelto por @Valid en el controller.
        // --- Paso 1.1.3 (CUIT): solo aplica a Persona Juridica, fuera de Sprint 1.

        // --- Paso 1.1.4: hashear la contrasena.
        // A partir de aca la contrasena en claro no vuelve a usarse ni a salir
        // del metodo. Nunca se loguea ni se persiste.
        String passwordHasheada = passwordEncoder.encode(peticion.getPassword());

        // --- Paso 1.1.5: identidad humana.
        Persona persona = new Persona();
        persona.setTipoPersona("FISICA");
        persona.setFechaRegistro(LocalDateTime.now());
        persona = personaRepository.save(persona);

        PersonaFisica personaFisica = new PersonaFisica();
        // Con @MapsId la clave primaria de persona_fisica se hereda de persona;
        // por eso se asigna la relacion y no el id a mano.
        personaFisica.setPersona(persona);
        personaFisica.setNombre(peticion.getNombre().trim());
        personaFisica.setApellido(peticion.getApellido().trim());
        personaFisica.setDni(peticion.getDni().trim());
        personaFisica.setFechaNacimiento(peticion.getFechaNacimiento());
        personaFisica = personaFisicaRepository.save(personaFisica);

        // --- Paso 1.1.6: cuenta de usuario en estado ACTIVO.
        UsuarioEstado estadoActivo = usuarioEstadoRepository
                .findByEstadoUsuarioIgnoreCase(EstadoUsuarioNombre.ACTIVO.name())
                .orElseThrow(() -> new ReglaNegocioException(
                        "El catalogo de estados de usuario no esta inicializado"));

        Usuario usuario = new Usuario();
        usuario.setPersonaFisica(personaFisica);
        usuario.setEmail(email);
        usuario.setNickname(nickname);
        usuario.setPassword(passwordHasheada);
        usuario.setEstadoUsuario(estadoActivo);
        usuario.setIntentosFallidos(0);
        usuario.setRequiereCaptcha(false);
        usuario.setFechaDesbloqueoCooldown(null);
        usuario.setFechaBaja(null);          // null = cuenta vigente (RF-1.6)
        usuario = usuarioRepository.save(usuario);

        // --- Paso 1.1.6: asignar rol(es).
        // Si elige ORGANIZADOR, obtiene ambos roles (ORGANIZADOR + PARTICIPANTE)
        // para poder crear eventos Y participar en los de otros.
        // Los PARTICIPANTES reciben solo ese rol.
        RolNombre rolElegido = RolNombre.valueOf(peticion.getPerfil().toUpperCase());
        List<String> rolesAsignados = new ArrayList<>();

        // Asignar el rol elegido
        Rol rol = rolRepository.findByNombreRolIgnoreCase(rolElegido.name())
                .orElseThrow(() -> new ReglaNegocioException(
                        "El rol " + rolElegido + " no esta cargado en el catalogo"));

        UsuarioRol usuarioRol = new UsuarioRol();
        usuarioRol.setUsuario(usuario);
        usuarioRol.setRol(rol);
        usuarioRolRepository.save(usuarioRol);
        rolesAsignados.add(rolElegido.name());

        // Si es ORGANIZADOR, agregar tambien el rol PARTICIPANTE
        if (rolElegido == RolNombre.ORGANIZADOR) {
            Rol rolParticipante = rolRepository.findByNombreRolIgnoreCase(RolNombre.PARTICIPANTE.name())
                    .orElseThrow(() -> new ReglaNegocioException(
                            "El rol PARTICIPANTE no esta cargado en el catalogo"));

            UsuarioRol usuarioRolParticipante = new UsuarioRol();
            usuarioRolParticipante.setUsuario(usuario);
            usuarioRolParticipante.setRol(rolParticipante);
            usuarioRolRepository.save(usuarioRolParticipante);
            rolesAsignados.add(RolNombre.PARTICIPANTE.name());
        }

        // --- Paso 1.1.7 (ente corporativo): Sprint 2.

        auditoriaService.registrar(usuario, AuditoriaService.ACCION_REGISTRO_EXITOSO,
                "Alta de cuenta con perfil " + rolElegido + ", roles asignados: " + rolesAsignados, request);
        log.info("Usuario {} registrado con roles {}", usuario.getIdUsuario(), rolesAsignados);

        return new UsuarioRegistroResponse(
                usuario.getIdUsuario(),
                usuario.getEmail(),
                usuario.getNickname(),
                EstadoUsuarioNombre.ACTIVO.name(),
                rolesAsignados,
                "Cuenta creada correctamente. Ya podes iniciar sesion.");
    }

    /**
     * Paso 1.1.1A: pasa por el filtro de moderacion los campos de texto libre
     * que despues van a ser visibles para otros usuarios.
     */
    private void moderarCamposDePerfil(UsuarioRegistroRequest peticion, HttpServletRequest request) {
        try {
            moderacionService.validar(peticion.getNickname(), "nickname");
            moderacionService.validar(peticion.getNombre(), "nombre");
            moderacionService.validar(peticion.getApellido(), "apellido");

        } catch (ContenidoInapropiadoException ex) {
            // El DFD pide dejar constancia del rechazo en auditoria. Va en
            // transaccion aparte porque el registro se revierte al propagarse
            // esta excepcion, y el rechazo tiene que quedar asentado igual.
            // No hay riesgo de interbloqueo: todavia no se creo ninguna fila.
            auditoriaService.registrarAparte(null, AuditoriaService.ACCION_REGISTRO_RECHAZADO_MODERACION,
                    "Registro rechazado por moderacion de texto", request);
            throw ex;
        }
    }
}
