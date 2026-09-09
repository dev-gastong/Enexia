package com.enexia.rg.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.enexia.rg.dto.OrganizacionResponse;
import com.enexia.rg.dto.RegistroConOrganizacionRequest;
import com.enexia.rg.dto.RegistroConOrganizacionResponse;
import com.enexia.rg.dto.UsuarioLoginRequest;
import com.enexia.rg.dto.UsuarioLoginResponse;
import com.enexia.rg.dto.UsuarioRegistroRequest;
import com.enexia.rg.dto.UsuarioRegistroResponse;
import com.enexia.rg.exception.ContenidoInapropiadoException;
import com.enexia.rg.exception.CredencialesInvalidasException;
import com.enexia.rg.exception.CuentaBloqueadaException;
import com.enexia.rg.exception.CuentaEnCooldownException;
import com.enexia.rg.exception.CuentaNoHabilitadaException;
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
 * CAMBIO DE POLITICA DE SEGURIDAD (2026-09-08, decision del usuario)
 *
 * 1. SE ELIMINO EL RATE LIMITING POR IP (antes, paso 1.2.1 del DFD).
 *    Motivo: detras de un CGNAT o del wifi de una institucion, cientos de
 *    dispositivos legitimos comparten una unica IP publica. Bloquear esa IP
 *    dejaba sin servicio a toda una zona por culpa de un solo atacante: una
 *    denegacion de servicio que el propio atacante podia provocar a voluntad.
 *
 * 2. EL BLOQUEO DE CUENTA PASO A SER SILENCIOSO.
 *    Todos los rechazos de login responden identico: 401, codigo
 *    CREDENCIALES_INVALIDAS, mismo texto, sin cabeceras extra y con el mismo
 *    costo en tiempo. El atacante no puede distinguir "email inexistente" de
 *    "contrasena incorrecta" ni de "cuenta bloqueada": ni por el cuerpo, ni por
 *    el status, ni por las cabeceras, ni por el reloj. Al titular legitimo se le
 *    avisa por email, con un enlace de recuperacion: es el unico canal que el
 *    atacante no controla (ver RecuperacionCuentaService).
 *
 * CONSECUENCIA CONOCIDA Y ASUMIDA: sin control por IP, el "password spraying"
 * (una contrasena comun probada contra miles de emails distintos) ya no tiene
 * freno propio, porque ninguna cuenta acumula fallos. Queda anotado como riesgo
 * abierto para el proximo sprint; la mitigacion natural es alertar por volumen
 * anomalo en historial_interacciones sin llegar a rechazar peticiones.
 *
 * El registro de Persona Juridica vive en PersonaJuridicaService (RF-7.2).
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
    private final IntentosLoginService intentosLoginService;
    private final RecuperacionCuentaService recuperacionCuentaService;
    private final PersonaJuridicaService personaJuridicaService;

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

        // --- Paso 1.2.4: recuperar cuenta y campos de control.
        // (El paso 1.2.1, rate limiting por IP, se retiro: ver javadoc de la clase.)
        Usuario usuario = usuarioRepository
                .buscarActivoPorEmailConRoles(peticion.getEmail())
                .orElse(null);

        if (usuario == null) {
            // El email no existe. Se gasta igual el tiempo de un BCrypt real
            // para no responder mas rapido que en el caso "existe pero la clave
            // esta mal": esa diferencia de milisegundos, medida muchas veces,
            // le revela a un atacante que emails estan registrados.
            nivelarTiempoDeRespuesta(peticion.getPassword());
            auditoriaService.registrarAparte(null, AuditoriaService.ACCION_LOGIN_FALLIDO,
                    "Intento contra un email no registrado", request);
            throw new CredencialesInvalidasException(
                    CredencialesInvalidasException.CODIGO_EMAIL_INEXISTENTE);
        }

        // --- Paso 1.2.4: el estado debe ser ACTIVO (RF-1.6).
        verificarEstadoHabilitado(usuario, peticion, request);

        // --- Paso 1.2.4: cooldown vigente.
        verificarCooldown(usuario, peticion);

        // --- Paso 1.2.4A (CAPTCHA): fuera de alcance en Sprint 1.
        // El flag requiere_captcha ya se marca en IntentosLoginService; solo
        // falta validar el token contra el proveedor externo.

        // --- Paso 1.2.5: comparar contrasena contra el hash BCrypt.
        if (!passwordEncoder.matches(peticion.getPassword(), usuario.getPassword())) {
            procesarIntentoFallido(usuario, request);
            throw new CredencialesInvalidasException(
                    CredencialesInvalidasException.CODIGO_PASSWORD_INCORRECTA);
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
     * Consume el mismo tiempo de CPU que una verificacion real de contrasena.
     *
     * POR QUE ES IMPRESCINDIBLE EN TODAS LAS RAMAS DE RECHAZO
     * Uniformar el cuerpo y el status de la respuesta no alcanza: el TIEMPO es
     * un canal lateral igual de expresivo. Si el rechazo por cuenta bloqueada
     * saliera sin pasar por BCrypt, responderia en ~1ms contra los ~250ms del
     * rechazo por contrasena incorrecta. Con unas pocas mediciones desde las
     * DevTools del navegador, esa diferencia delata exactamente aquello que la
     * respuesta uniforme trata de ocultar.
     *
     * El hash senuelo se calcula una sola vez al arrancar y tiene el mismo costo
     * (factor 12) que los hashes reales de la base, asi que compararlo cuesta lo
     * mismo que comparar el de un usuario existente.
     */
    private void nivelarTiempoDeRespuesta(String passwordCandidata) {
        passwordEncoder.matches(passwordCandidata, hashSenuelo);
    }

    /**
     * Verifica que el estado de la cuenta habilite el ingreso (RF-1.6).
     *
     * CAMBIO 2026-09-08: BLOQUEADO ya no se distingue con mensaje propio. Antes,
     * el 403 "Cuenta bloqueada por seguridad" le confirmaba al atacante que el
     * email existe Y que su ataque de fuerza bruta funciono. Ahora los cuatro
     * estados no habilitados salen por el mismo 401 generico; la diferencia
     * queda en el codigo interno de la excepcion, que solo ve el servidor.
     */
    private void verificarEstadoHabilitado(Usuario usuario, UsuarioLoginRequest peticion,
                                           HttpServletRequest request) {
        UsuarioEstado estado = usuario.getEstadoUsuario();
        String nombreEstado = estado != null ? estado.getEstadoUsuario() : null;

        if (EstadoUsuarioNombre.ACTIVO.name().equalsIgnoreCase(nombreEstado)) {
            return;
        }

        nivelarTiempoDeRespuesta(peticion.getPassword());

        auditoriaService.registrarAparte(usuario, AuditoriaService.ACCION_LOGIN_FALLIDO,
                "Intento sobre cuenta en estado " + nombreEstado, request);

        if (EstadoUsuarioNombre.BLOQUEADO.name().equalsIgnoreCase(nombreEstado)) {
            throw new CuentaBloqueadaException();
        }
        throw new CuentaNoHabilitadaException(nombreEstado);
    }

    /**
     * Paso 1.2.4: rechaza si todavia corre la penalizacion temporal.
     *
     * No se compara la contrasena durante el cooldown (ese es justamente el
     * sentido de la penalizacion), pero SI se paga el costo del BCrypt senuelo,
     * para que este rechazo no llegue antes que un rechazo normal.
     */
    private void verificarCooldown(Usuario usuario, UsuarioLoginRequest peticion) {
        LocalDateTime cooldown = usuario.getFechaDesbloqueoCooldown();
        if (cooldown != null && LocalDateTime.now().isBefore(cooldown)) {
            nivelarTiempoDeRespuesta(peticion.getPassword());
            throw new CuentaEnCooldownException(cooldown);
        }
    }

    /** Pasos 1.2.6 y 1.2.7: contabiliza el fallo, penaliza, audita y avisa. */
    private void procesarIntentoFallido(Usuario usuario, HttpServletRequest request) {
        boolean quedoBloqueada = intentosLoginService.registrarFallo(usuario.getIdUsuario());

        auditoriaService.registrarAparte(usuario, AuditoriaService.ACCION_LOGIN_FALLIDO,
                "Contrasena incorrecta", request);

        if (quedoBloqueada) {
            auditoriaService.registrarAparte(usuario, AuditoriaService.ACCION_CUENTA_BLOQUEADA,
                    "Bloqueo automatico por acumulacion de intentos fallidos", request);

            // Paso 1.2.7A: el aviso de seguridad. Es la pieza que compensa el
            // silencio de la respuesta HTTP: el titular se entera por su casilla,
            // que el atacante no controla, y recibe ahi mismo el enlace para
            // recuperar el acceso (RF-1.5).
            recuperacionCuentaService.notificarBloqueoConEnlaceDeRecuperacion(usuario.getIdUsuario());

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
        Usuario usuario = crearCuentaPersonaFisica(peticion, request);

        return new UsuarioRegistroResponse(
                usuario.getIdUsuario(),
                usuario.getEmail(),
                usuario.getNickname(),
                EstadoUsuarioNombre.ACTIVO.name(),
                usuario.getRoles(),
                "Cuenta creada correctamente. Ya podes iniciar sesion.");
    }

    /**
     * Alta en un solo paso: cuenta personal + organizacion (DFD 7.1/7.2).
     *
     * POR QUE LAS DOS COSAS EN UNA UNICA TRANSACCION
     * El DFD muestra la bifurcacion Fisica/Juridica dentro del mismo formulario:
     * para el usuario es UN acto. Si la organizacion fallara despues de crear la
     * cuenta, quedaria una cuenta de organizador sin empresa y el usuario
     * volveria a enviar el formulario completo, chocando ahora con "ese email ya
     * esta registrado". Todo junto, o no queda nada.
     *
     * El otro camino (POST /api/organizador/organizaciones, autenticado) existe
     * para quien ya tiene cuenta y crea la empresa despues, tal como lo describe
     * RF-7.2. Ambos terminan en el mismo metodo de PersonaJuridicaService.
     */
    @Transactional
    public RegistroConOrganizacionResponse registrarConOrganizacion(
            RegistroConOrganizacionRequest peticion, HttpServletRequest request) {

        // Se exige explicitamente en vez de corregirlo en silencio: recibir
        // PARTICIPANTE junto con datos fiscales significa que el cliente esta
        // armando mal la peticion, y taparlo solo retrasa el diagnostico.
        if (!RolNombre.ORGANIZADOR.name().equalsIgnoreCase(peticion.getPerfil())) {
            throw new ReglaNegocioException(
                    "Para registrar una organizacion el perfil debe ser ORGANIZADOR");
        }

        Usuario usuario = crearCuentaPersonaFisica(peticion, request);

        // Se pasa la ENTIDAD, no el email: dentro de la misma transaccion el
        // usuario todavia podria no estar visible para una consulta JPQL, y
        // ademas asi se evita una lectura innecesaria.
        OrganizacionResponse organizacion =
                personaJuridicaService.crearOrganizacion(usuario, peticion.getOrganizacion(), request);

        UsuarioRegistroResponse cuenta = new UsuarioRegistroResponse(
                usuario.getIdUsuario(),
                usuario.getEmail(),
                usuario.getNickname(),
                EstadoUsuarioNombre.ACTIVO.name(),
                usuario.getRoles(),
                "Cuenta creada correctamente. Ya podes iniciar sesion.");

        return new RegistroConOrganizacionResponse(cuenta, organizacion);
    }

    /**
     * Nucleo del alta de Persona Fisica: escribe las cuatro tablas y devuelve la
     * entidad, con sus roles ya cargados en memoria.
     *
     * Se separo de {@link #registrar} para que el alta con organizacion pueda
     * reutilizarla sin duplicar una linea de logica. Devuelve {@link Usuario} y
     * no el DTO porque quien la llama a veces necesita la entidad (para pasarsela
     * a PersonaJuridicaService) y a veces solo el DTO.
     */
    private Usuario crearCuentaPersonaFisica(UsuarioRegistroRequest peticion, HttpServletRequest request) {

        // --- Coherencia entre ambas contrasenas.
        // No lo cubre @Valid: las anotaciones miran un campo por vez y esto
        // compara dos entre si.
        if (!peticion.getPassword().equals(peticion.getPasswordConfirmacion())) {
            throw new ReglaNegocioException("Las contrasenas no coinciden");
        }

        String email = peticion.getEmail().trim().toLowerCase();
        String nickname = peticion.getNickname().trim();
        String dni = peticion.getDni().trim();

        // --- Paso 1.1.1: unicidad de credenciales.
        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new RecursoDuplicadoException("Ya existe una cuenta registrada con ese email");
        }
        if (usuarioRepository.existsByNicknameIgnoreCase(nickname)) {
            throw new RecursoDuplicadoException("Ese nickname ya esta en uso");
        }

        // --- Paso 7.1.2: unicidad del documento.
        // Email y nickname identifican a la CUENTA; el DNI identifica a la
        // PERSONA. Sin este control, alguien podria abrir cuentas ilimitadas
        // cambiando solo el email, y una suspension no serviria de nada porque
        // el mismo individuo volveria a entrar con otra cuenta.
        if (personaFisicaRepository.existsByDni(dni)) {
            throw new RecursoDuplicadoException("Ya existe una cuenta registrada con ese DNI");
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
        // Ya no se fija tipo_persona: la columna se elimino el 2026-09-08.
        // Toda fila de 'persona' es una persona humana; las organizaciones viven
        // en persona_juridica, sin herencia entre ambas. Ver Persona.java.
        persona.setFechaRegistro(LocalDateTime.now());
        persona = personaRepository.save(persona);

        PersonaFisica personaFisica = new PersonaFisica();
        // Con @MapsId la clave primaria de persona_fisica se hereda de persona;
        // por eso se asigna la relacion y no el id a mano.
        personaFisica.setPersona(persona);
        personaFisica.setNombre(peticion.getNombre().trim());
        personaFisica.setApellido(peticion.getApellido().trim());
        personaFisica.setDni(dni);
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
        // LinkedHashSet y no HashSet: preserva el orden de asignacion, asi el
        // rol elegido por el usuario aparece primero en la respuesta y en el JWT.
        Set<UsuarioRol> rolesPersistidos = new LinkedHashSet<>();

        // Asignar el rol elegido
        Rol rol = rolRepository.findByNombreRolIgnoreCase(rolElegido.name())
                .orElseThrow(() -> new ReglaNegocioException(
                        "El rol " + rolElegido + " no esta cargado en el catalogo"));

        UsuarioRol usuarioRol = new UsuarioRol();
        usuarioRol.setUsuario(usuario);
        usuarioRol.setRol(rol);
        usuarioRolRepository.save(usuarioRol);
        // Se agrega la instancia LOCAL, no lo que devuelva save(). El objeto ya
        // esta completo y asociado; depender del retorno ata esta linea a un
        // detalle del repositorio que no aporta nada.
        rolesPersistidos.add(usuarioRol);
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
            rolesPersistidos.add(usuarioRolParticipante);
            rolesAsignados.add(RolNombre.PARTICIPANTE.name());
        }

        // --- Paso 1.1.7 (ente corporativo): ver PersonaJuridicaService (RF-7.2).

        // La coleccion se completa a mano porque la entidad recien creada no la
        // recarga sola: sin esto, usuario.getRoles() devolveria vacio dentro de
        // la misma transaccion y el control de rol del alta de organizacion
        // rechazaria a un ORGANIZADOR legitimo.
        usuario.setUsuarioRoles(rolesPersistidos);

        auditoriaService.registrar(usuario, AuditoriaService.ACCION_REGISTRO_EXITOSO,
                "Alta de cuenta con perfil " + rolElegido + ", roles asignados: " + rolesAsignados, request);
        log.info("Usuario {} registrado con roles {}", usuario.getIdUsuario(), rolesAsignados);

        return usuario;
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

    // =====================================================================
    // GET ME  (Endpoint autenticado para pruebas)
    // =====================================================================

    /**
     * Recupera los datos del usuario autenticado segun el token.
     * Usado por la pagina de prueba (prueba-token.html) para verificar que el
     * token se inyecta correctamente en los headers de las requests posteriores.
     */
    public UsuarioLoginResponse obtenerDatosActual(String email) {
        Usuario usuario = usuarioRepository
                .buscarActivoPorEmailConRoles(email)
                .orElseThrow(CredencialesInvalidasException::new);

        return new UsuarioLoginResponse(
                usuario.getIdUsuario(),
                usuario.getEmail(),
                null,  // No devolvemos el token en esta respuesta
                "Bearer",
                usuario.getRoles());
    }
}
