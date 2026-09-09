package com.enexia.rg.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.enexia.rg.dto.UsuarioLoginRequest;
import com.enexia.rg.dto.UsuarioLoginResponse;
import com.enexia.rg.exception.AutenticacionFallidaException;
import com.enexia.rg.exception.CredencialesInvalidasException;
import com.enexia.rg.exception.CuentaBloqueadaException;
import com.enexia.rg.exception.CuentaEnCooldownException;
import com.enexia.rg.exception.CuentaNoHabilitadaException;
import com.enexia.rg.model.EstadoUsuarioNombre;
import com.enexia.rg.model.Rol;
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

import jakarta.servlet.http.HttpServletRequest;

/**
 * Pruebas de {@link AuthService#login} bajo la POLITICA DE BLOQUEO SILENCIOSO
 * adoptada el 2026-09-08.
 *
 * QUE SE ESTA PROBANDO EN REALIDAD
 * No es "que el login funcione": eso lo cubre un solo test. Lo que se verifica
 * aca, caso por caso, es que el login NO FILTRE INFORMACION. Cada rama de
 * rechazo -- email inexistente, contrasena incorrecta, cuenta bloqueada, cuenta
 * en cooldown, cuenta suspendida -- tiene que ser indistinguible de las demas
 * para quien esta del otro lado, y distinguible para quien lee el log.
 *
 * Son tres propiedades independientes y cada una necesita su prueba:
 *   1. MISMO TIPO: todas heredan de AutenticacionFallidaException, que el
 *      handler global traduce a un unico 401.
 *   2. MISMO MENSAJE: el texto publico es siempre el mismo.
 *   3. MISMO TIEMPO: todas pagan el costo de un BCrypt. Sin esto, las tres
 *      primeras propiedades no sirven de nada: el reloj delata igual.
 *
 * La tercera es la mas facil de romper sin darse cuenta al tocar el codigo, y
 * por eso se verifica contando invocaciones a passwordEncoder.matches().
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthService.login() - bloqueo silencioso y respuesta uniforme (RF-1.2, RF-1.4, RF-1.6)")
class AuthServiceLoginTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PersonaRepository personaRepository;
    @Mock private PersonaFisicaRepository personaFisicaRepository;
    @Mock private UsuarioRolRepository usuarioRolRepository;
    @Mock private RolRepository rolRepository;
    @Mock private UsuarioEstadoRepository usuarioEstadoRepository;

    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private ModeracionTextoService moderacionService;
    @Mock private AuditoriaService auditoriaService;
    @Mock private IntentosLoginService intentosLoginService;
    @Mock private RecuperacionCuentaService recuperacionCuentaService;
    @Mock private PersonaJuridicaService personaJuridicaService;

    @Mock private HttpServletRequest request;

    @InjectMocks private AuthService authService;

    private static final String EMAIL = "ana@enexia.test";
    private static final String PASSWORD = "Segura123";
    private static final String HASH = "$2a$12$hashDePruebaNoEsUnBCryptReal";

    private UsuarioLoginRequest peticion;

    @BeforeEach
    void prepararEscenario() {
        // El hash senuelo lo calcula @PostConstruct, que Mockito no ejecuta:
        // se invoca a mano para que nivelarTiempoDeRespuesta() tenga con que
        // comparar. Sin esto, el campo quedaria null y no se podria verificar
        // la propiedad de tiempo constante.
        when(passwordEncoder.encode(anyString())).thenReturn(HASH);
        authService.prepararHashSenuelo();

        peticion = new UsuarioLoginRequest();
        peticion.setEmail(EMAIL);
        peticion.setPassword(PASSWORD);
    }

    // =====================================================================
    // Camino feliz
    // =====================================================================

    @Test
    @DisplayName("Credenciales correctas: emite JWT con los roles y limpia contadores")
    void loginExitoso() {
        Usuario usuario = usuarioActivo();
        when(usuarioRepository.buscarActivoPorEmailConRoles(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(jwtService.generarToken(eq(EMAIL), any())).thenReturn("token.jwt.firmado");

        UsuarioLoginResponse respuesta = authService.login(peticion, request);

        assertThat(respuesta.getToken()).isEqualTo("token.jwt.firmado");
        assertThat(respuesta.getRoles()).containsExactly("ORGANIZADOR");
        // Paso 1.2.5A: sin este reseteo, los fallos se acumularian por meses y
        // un usuario legitimo terminaria bloqueado por errores de tipeo sueltos.
        verify(intentosLoginService).limpiarTrasLoginExitoso(1L);
    }

    // =====================================================================
    // La propiedad central: todas las ramas responden igual
    // =====================================================================

    @Nested
    @DisplayName("Respuesta uniforme: ninguna rama de rechazo se distingue de otra")
    class RespuestaUniforme {

        @Test
        @DisplayName("Email inexistente -> excepcion de la familia, motivo interno EMAIL_INEXISTENTE")
        void emailInexistente() {
            when(usuarioRepository.buscarActivoPorEmailConRoles(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(peticion, request))
                    .isInstanceOf(CredencialesInvalidasException.class)
                    .hasMessage(AutenticacionFallidaException.MENSAJE_PUBLICO);
        }

        @Test
        @DisplayName("Contrasena incorrecta -> mismo mensaje publico")
        void passwordIncorrecta() {
            Usuario usuario = usuarioActivo();
            when(usuarioRepository.buscarActivoPorEmailConRoles(EMAIL)).thenReturn(Optional.of(usuario));
            when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(false);
            when(intentosLoginService.registrarFallo(1L)).thenReturn(false);

            assertThatThrownBy(() -> authService.login(peticion, request))
                    .isInstanceOf(CredencialesInvalidasException.class)
                    .hasMessage(AutenticacionFallidaException.MENSAJE_PUBLICO);
        }

        @Test
        @DisplayName("Cuenta BLOQUEADA -> mismo mensaje publico, NO 'cuenta bloqueada'")
        void cuentaBloqueada() {
            Usuario usuario = usuarioConEstado(EstadoUsuarioNombre.BLOQUEADO);
            when(usuarioRepository.buscarActivoPorEmailConRoles(EMAIL)).thenReturn(Optional.of(usuario));

            assertThatThrownBy(() -> authService.login(peticion, request))
                    .isInstanceOf(CuentaBloqueadaException.class)
                    // La clase concreta es distinta -- hace falta para auditar --
                    // pero el TEXTO que ve el atacante es identico al de un
                    // email inexistente. Ese es todo el punto de la politica.
                    .hasMessage(AutenticacionFallidaException.MENSAJE_PUBLICO);
        }

        @Test
        @DisplayName("Cuenta SUSPENDIDA -> mismo mensaje publico")
        void cuentaSuspendida() {
            Usuario usuario = usuarioConEstado(EstadoUsuarioNombre.SUSPENDIDO);
            when(usuarioRepository.buscarActivoPorEmailConRoles(EMAIL)).thenReturn(Optional.of(usuario));

            assertThatThrownBy(() -> authService.login(peticion, request))
                    .isInstanceOf(CuentaNoHabilitadaException.class)
                    .hasMessage(AutenticacionFallidaException.MENSAJE_PUBLICO);
        }

        @Test
        @DisplayName("Cooldown vigente -> mismo mensaje publico y el momento NO se publica")
        void cuentaEnCooldown() {
            Usuario usuario = usuarioActivo();
            LocalDateTime finCooldown = LocalDateTime.now().plusMinutes(5);
            usuario.setFechaDesbloqueoCooldown(finCooldown);
            when(usuarioRepository.buscarActivoPorEmailConRoles(EMAIL)).thenReturn(Optional.of(usuario));

            assertThatThrownBy(() -> authService.login(peticion, request))
                    .isInstanceOf(CuentaEnCooldownException.class)
                    .hasMessage(AutenticacionFallidaException.MENSAJE_PUBLICO)
                    // El dato existe para el log, pero el mensaje no lo menciona:
                    // publicarlo (como hacia la cabecera X-Reintentar-Despues)
                    // le decia al atacante cuando reanudar el ataque.
                    .extracting(ex -> ((CuentaEnCooldownException) ex).getDisponibleDesde())
                    .isEqualTo(finCooldown);
        }

        @Test
        @DisplayName("Las cinco ramas comparten la misma raiz, que el handler traduce a un unico 401")
        void todasCompartenLaRaiz() {
            // Si alguien agrega manana una excepcion de login que NO herede de
            // AutenticacionFallidaException, el handler global no la va a
            // atrapar: caeria en el catch-all como 500 ERROR_INTERNO y esa
            // diferencia bastaria para distinguir la rama desde afuera.
            assertThat(AutenticacionFallidaException.class)
                    .isAssignableFrom(CredencialesInvalidasException.class)
                    .isAssignableFrom(CuentaBloqueadaException.class)
                    .isAssignableFrom(CuentaEnCooldownException.class)
                    .isAssignableFrom(CuentaNoHabilitadaException.class);
        }
    }

    // =====================================================================
    // La propiedad que se rompe sin darse cuenta: el tiempo
    // =====================================================================

    @Nested
    @DisplayName("Tiempo constante: toda rama de rechazo paga un BCrypt")
    class TiempoConstante {

        @Test
        @DisplayName("Email inexistente: se compara igual contra el hash senuelo")
        void emailInexistenteGastaBcrypt() {
            when(usuarioRepository.buscarActivoPorEmailConRoles(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(peticion, request))
                    .isInstanceOf(CredencialesInvalidasException.class);

            // Sin esta comparacion, la respuesta llegaria en ~1ms contra los
            // ~250ms de un email existente, y midiendo el tiempo se podria
            // enumerar que correos estan registrados.
            verify(passwordEncoder, times(1)).matches(PASSWORD, HASH);
        }

        @Test
        @DisplayName("Cuenta BLOQUEADA: tambien paga el BCrypt antes de rechazar")
        void bloqueadaGastaBcrypt() {
            Usuario usuario = usuarioConEstado(EstadoUsuarioNombre.BLOQUEADO);
            when(usuarioRepository.buscarActivoPorEmailConRoles(EMAIL)).thenReturn(Optional.of(usuario));

            assertThatThrownBy(() -> authService.login(peticion, request))
                    .isInstanceOf(CuentaBloqueadaException.class);

            // Esta era la fuga mas facil de dejar abierta: el rechazo por estado
            // ocurre ANTES de comparar la contrasena, asi que sin el senuelo
            // saldria mucho mas rapido que un rechazo normal.
            verify(passwordEncoder, times(1)).matches(anyString(), anyString());
        }

        @Test
        @DisplayName("Cooldown: paga el BCrypt aunque no compare la contrasena real")
        void cooldownGastaBcrypt() {
            Usuario usuario = usuarioActivo();
            usuario.setFechaDesbloqueoCooldown(LocalDateTime.now().plusMinutes(5));
            when(usuarioRepository.buscarActivoPorEmailConRoles(EMAIL)).thenReturn(Optional.of(usuario));

            assertThatThrownBy(() -> authService.login(peticion, request))
                    .isInstanceOf(CuentaEnCooldownException.class);

            verify(passwordEncoder, times(1)).matches(anyString(), anyString());
        }
    }

    // =====================================================================
    // El canal que reemplaza a la respuesta HTTP
    // =====================================================================

    @Nested
    @DisplayName("Aviso al titular: el email es el unico canal que el atacante no controla")
    class AvisoPorEmail {

        @Test
        @DisplayName("Al dispararse el bloqueo se emite el enlace de recuperacion")
        void avisaAlBloquear() {
            Usuario usuario = usuarioActivo();
            when(usuarioRepository.buscarActivoPorEmailConRoles(EMAIL)).thenReturn(Optional.of(usuario));
            when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(false);
            // El intento que cruza el umbral.
            when(intentosLoginService.registrarFallo(1L)).thenReturn(true);

            assertThatThrownBy(() -> authService.login(peticion, request))
                    .isInstanceOf(CuentaBloqueadaException.class);

            // Sin este aviso la politica seria incompleta: el titular legitimo
            // no tendria ninguna forma de enterarse de que lo bloquearon, porque
            // la pantalla le dice lo mismo que a cualquiera.
            verify(recuperacionCuentaService).notificarBloqueoConEnlaceDeRecuperacion(1L);
            verify(auditoriaService).registrarAparte(eq(usuario),
                    eq(AuditoriaService.ACCION_CUENTA_BLOQUEADA), anyString(), any());
        }

        @Test
        @DisplayName("Un fallo que NO bloquea no manda ningun correo")
        void noAvisaSiNoBloquea() {
            Usuario usuario = usuarioActivo();
            when(usuarioRepository.buscarActivoPorEmailConRoles(EMAIL)).thenReturn(Optional.of(usuario));
            when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(false);
            when(intentosLoginService.registrarFallo(1L)).thenReturn(false);

            assertThatThrownBy(() -> authService.login(peticion, request))
                    .isInstanceOf(CredencialesInvalidasException.class);

            // Mandar un mail en cada error de tipeo entrenaria al usuario a
            // ignorar los avisos, que es la forma mas eficaz de inutilizarlos.
            verify(recuperacionCuentaService, never())
                    .notificarBloqueoConEnlaceDeRecuperacion(anyLong());
        }
    }

    // =====================================================================
    // Auxiliares
    // =====================================================================

    private Usuario usuarioActivo() {
        return usuarioConEstado(EstadoUsuarioNombre.ACTIVO);
    }

    private Usuario usuarioConEstado(EstadoUsuarioNombre nombreEstado) {
        UsuarioEstado estado = new UsuarioEstado();
        estado.setEstadoUsuario(nombreEstado.name());

        Rol rol = new Rol();
        rol.setNombreRol("ORGANIZADOR");

        Usuario usuario = new Usuario();
        usuario.setIdUsuario(1L);
        usuario.setEmail(EMAIL);
        usuario.setPassword(HASH);
        usuario.setEstadoUsuario(estado);
        usuario.setIntentosFallidos(0);

        UsuarioRol usuarioRol = new UsuarioRol();
        usuarioRol.setUsuario(usuario);
        usuarioRol.setRol(rol);
        usuario.setUsuarioRoles(Set.of(usuarioRol));

        assertThat(usuario.getRoles()).isEqualTo(List.of("ORGANIZADOR"));
        return usuario;
    }
}
