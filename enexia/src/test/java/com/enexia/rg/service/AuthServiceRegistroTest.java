package com.enexia.rg.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.enexia.rg.dto.UsuarioRegistroRequest;
import com.enexia.rg.dto.UsuarioRegistroResponse;
import com.enexia.rg.exception.ContenidoInapropiadoException;
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

import jakarta.servlet.http.HttpServletRequest;

/**
 * Pruebas unitarias de {@link AuthService#registrar}.
 *
 * Son unitarias en sentido estricto: no levantan Spring, no tocan MySQL y no
 * abren un navegador. Todas las dependencias del service son dobles de prueba
 * (@Mock), asi que lo unico bajo examen es la logica del metodo. Por eso corren
 * en milisegundos y pueden ejecutarse sin la aplicacion encendida.
 *
 * La contraparte de extremo a extremo esta en RegistroTest (Playwright), que si
 * necesita el backend arriba. Las dos capas se complementan: aca se cubren las
 * ramas de error una por una; alla se comprueba que el formulario real las
 * dispare y las muestre.
 *
 * Cobertura, siguiendo el DFD docs/diagrams/login_registro/registro.md:
 *   - Camino feliz PARTICIPANTE y ORGANIZADOR (paso 1.1.6, asignacion de roles)
 *   - 1.1.1  unicidad de email, nickname y DNI
 *   - 1.1.1A moderacion de texto y su auditoria
 *   - Coherencia de contrasenas (previo a 1.1.4)
 *   - 1.1.4  hasheo BCrypt: la contrasena en claro nunca se persiste
 *   - Catalogos ausentes (estado / rol no inicializados)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthService.registrar() - alta de Persona Fisica (RF-1.1)")
class AuthServiceRegistroTest {

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

    private static final String HASH_FALSO = "$2a$10$hashDePruebaNoEsUnBCryptReal";

    /**
     * Deja el escenario en "todo disponible y nada duplicado". Cada prueba
     * cambia solo lo que necesita romper, asi el motivo del fallo queda a la
     * vista en el propio test en lugar de esconderse en el setup.
     */
    @BeforeEach
    void prepararEscenario() {
        when(usuarioRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(usuarioRepository.existsByNicknameIgnoreCase(anyString())).thenReturn(false);
        when(personaFisicaRepository.existsByDni(anyString())).thenReturn(false);

        when(passwordEncoder.encode(anyString())).thenReturn(HASH_FALSO);

        // Los save() de JPA devuelven la entidad gestionada; aca se devuelve la
        // misma instancia con un id puesto a mano, que es lo que el service
        // necesita para encadenar persona -> persona_fisica -> usuario.
        when(personaRepository.save(any(Persona.class))).thenAnswer(inv -> {
            Persona p = inv.getArgument(0);
            p.setIdPersona(1L);
            return p;
        });
        when(personaFisicaRepository.save(any(PersonaFisica.class))).thenAnswer(inv -> {
            PersonaFisica pf = inv.getArgument(0);
            pf.setIdPersona(1L);
            return pf;
        });
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setIdUsuario(42L);
            return u;
        });

        when(usuarioEstadoRepository.findByEstadoUsuarioIgnoreCase(EstadoUsuarioNombre.ACTIVO.name()))
                .thenReturn(Optional.of(estado(EstadoUsuarioNombre.ACTIVO.name())));
        when(rolRepository.findByNombreRolIgnoreCase(RolNombre.PARTICIPANTE.name()))
                .thenReturn(Optional.of(rol(1L, RolNombre.PARTICIPANTE.name())));
        when(rolRepository.findByNombreRolIgnoreCase(RolNombre.ORGANIZADOR.name()))
                .thenReturn(Optional.of(rol(2L, RolNombre.ORGANIZADOR.name())));
    }

    // =====================================================================
    // Camino feliz
    // =====================================================================

    @Nested
    @DisplayName("Camino feliz")
    class CaminoFeliz {

        @Test
        @DisplayName("PARTICIPANTE: crea la cuenta ACTIVA y recibe un unico rol")
        void participanteRecibeSoloSuRol() {
            UsuarioRegistroResponse respuesta =
                    authService.registrar(peticionValida(RolNombre.PARTICIPANTE), request);

            assertThat(respuesta.getIdUsuario()).isEqualTo(42L);
            assertThat(respuesta.getEstado()).isEqualTo(EstadoUsuarioNombre.ACTIVO.name());
            assertThat(respuesta.getRoles()).containsExactly(RolNombre.PARTICIPANTE.name());

            // Un solo INSERT en usuario_rol.
            verify(usuarioRolRepository, times(1)).save(any(UsuarioRol.class));
        }

        @Test
        @DisplayName("ORGANIZADOR: recibe ORGANIZADOR + PARTICIPANTE (regla de CLAUDE.md)")
        void organizadorRecibeAmbosRoles() {
            UsuarioRegistroResponse respuesta =
                    authService.registrar(peticionValida(RolNombre.ORGANIZADOR), request);

            // La regla existe para que un organizador no tenga que abrir una
            // segunda cuenta solo para inscribirse a eventos ajenos.
            assertThat(respuesta.getRoles())
                    .containsExactly(RolNombre.ORGANIZADOR.name(), RolNombre.PARTICIPANTE.name());

            verify(usuarioRolRepository, times(2)).save(any(UsuarioRol.class));
        }

        @Test
        @DisplayName("El email se normaliza a minusculas y los textos se recortan")
        void normalizaEntradas() {
            UsuarioRegistroRequest peticion = peticionValida(RolNombre.PARTICIPANTE);
            peticion.setEmail("  Maria.Gonzalez@ENEXIA.COM  ");
            peticion.setNickname("  maria_g  ");
            peticion.setNombre("  Maria  ");

            authService.registrar(peticion, request);

            ArgumentCaptor<Usuario> capturado = ArgumentCaptor.forClass(Usuario.class);
            verify(usuarioRepository).save(capturado.capture());

            // Sin normalizar, "Maria@x.com" y "maria@x.com" serian dos cuentas
            // distintas y el chequeo de unicidad no serviria de nada.
            assertThat(capturado.getValue().getEmail()).isEqualTo("maria.gonzalez@enexia.com");
            assertThat(capturado.getValue().getNickname()).isEqualTo("maria_g");
        }

        @Test
        @DisplayName("La cuenta nace sin penalizaciones de login y sin fecha_baja (RF-1.6)")
        void cuentaNaceLimpia() {
            authService.registrar(peticionValida(RolNombre.PARTICIPANTE), request);

            ArgumentCaptor<Usuario> capturado = ArgumentCaptor.forClass(Usuario.class);
            verify(usuarioRepository).save(capturado.capture());
            Usuario guardado = capturado.getValue();

            assertThat(guardado.getIntentosFallidos()).isZero();
            assertThat(guardado.getRequiereCaptcha()).isFalse();
            assertThat(guardado.getFechaDesbloqueoCooldown()).isNull();
            // fecha_baja null = cuenta vigente. El borrado es logico: nunca se
            // elimina la fila, se le pone fecha.
            assertThat(guardado.getFechaBaja()).isNull();
        }

        @Test
        @DisplayName("Se deja constancia del alta en auditoria")
        void auditaElAlta() {
            authService.registrar(peticionValida(RolNombre.PARTICIPANTE), request);

            verify(auditoriaService).registrar(
                    any(Usuario.class),
                    eq(AuditoriaService.ACCION_REGISTRO_EXITOSO),
                    anyString(),
                    eq(request));
        }
    }

    // =====================================================================
    // Paso 1.1.4 - Contrasena
    // =====================================================================

    @Nested
    @DisplayName("Contrasena (paso 1.1.4)")
    class Contrasena {

        @Test
        @DisplayName("Se persiste el hash BCrypt, nunca la contrasena en claro")
        void persisteElHashYNoElTextoPlano() {
            UsuarioRegistroRequest peticion = peticionValida(RolNombre.PARTICIPANTE);

            authService.registrar(peticion, request);

            ArgumentCaptor<Usuario> capturado = ArgumentCaptor.forClass(Usuario.class);
            verify(usuarioRepository).save(capturado.capture());

            assertThat(capturado.getValue().getPassword()).isEqualTo(HASH_FALSO);
            assertThat(capturado.getValue().getPassword()).isNotEqualTo(peticion.getPassword());
            verify(passwordEncoder).encode("Segura123");
        }

        @Test
        @DisplayName("Contrasenas distintas: ReglaNegocioException y no se escribe nada")
        void rechazaContrasenasQueNoCoinciden() {
            UsuarioRegistroRequest peticion = peticionValida(RolNombre.PARTICIPANTE);
            peticion.setPasswordConfirmacion("OtraDistinta123");

            assertThatThrownBy(() -> authService.registrar(peticion, request))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("no coinciden");

            // La comparacion va primero: ni siquiera se consulta la base.
            verifyNoInteractions(usuarioRepository, personaRepository, personaFisicaRepository);
        }
    }

    // =====================================================================
    // Paso 1.1.1 - Unicidad
    // =====================================================================

    @Nested
    @DisplayName("Unicidad de identidad (paso 1.1.1)")
    class Unicidad {

        @Test
        @DisplayName("Email repetido: 409 RecursoDuplicado")
        void rechazaEmailDuplicado() {
            when(usuarioRepository.existsByEmailIgnoreCase(anyString())).thenReturn(true);

            assertThatThrownBy(() -> authService.registrar(peticionValida(RolNombre.PARTICIPANTE), request))
                    .isInstanceOf(RecursoDuplicadoException.class)
                    .hasMessageContaining("email");

            verify(personaRepository, never()).save(any());
        }

        @Test
        @DisplayName("Nickname repetido: 409 RecursoDuplicado")
        void rechazaNicknameDuplicado() {
            when(usuarioRepository.existsByNicknameIgnoreCase(anyString())).thenReturn(true);

            assertThatThrownBy(() -> authService.registrar(peticionValida(RolNombre.PARTICIPANTE), request))
                    .isInstanceOf(RecursoDuplicadoException.class)
                    .hasMessageContaining("nickname");

            verify(personaRepository, never()).save(any());
        }

        @Test
        @DisplayName("DNI repetido: 409 RecursoDuplicado (DFD 7.1.2)")
        void rechazaDniDuplicado() {
            when(personaFisicaRepository.existsByDni(anyString())).thenReturn(true);

            // Email y nickname identifican la CUENTA; el DNI identifica a la
            // PERSONA. Sin este control, una suspension se esquiva abriendo
            // otra cuenta con el mismo documento.
            assertThatThrownBy(() -> authService.registrar(peticionValida(RolNombre.PARTICIPANTE), request))
                    .isInstanceOf(RecursoDuplicadoException.class)
                    .hasMessageContaining("DNI");

            verify(personaRepository, never()).save(any());
            verify(usuarioRepository, never()).save(any());
        }

        @Test
        @DisplayName("El DNI se consulta ya recortado")
        void consultaElDniSinEspacios() {
            UsuarioRegistroRequest peticion = peticionValida(RolNombre.PARTICIPANTE);
            peticion.setDni("  40123456  ");

            authService.registrar(peticion, request);

            verify(personaFisicaRepository).existsByDni("40123456");
        }
    }

    // =====================================================================
    // Paso 1.1.1A - Moderacion
    // =====================================================================

    @Nested
    @DisplayName("Moderacion de texto (paso 1.1.1A)")
    class Moderacion {

        @Test
        @DisplayName("Nickname ofensivo: 422, se audita el rechazo y no se persiste nada")
        void rechazaYAuditaContenidoInapropiado() {
            doThrow(new ContenidoInapropiadoException("nickname"))
                    .when(moderacionService).validar(anyString(), eq("nickname"));

            assertThatThrownBy(() -> authService.registrar(peticionValida(RolNombre.PARTICIPANTE), request))
                    .isInstanceOf(ContenidoInapropiadoException.class);

            // El DFD pide dejar constancia del intento rechazado.
            verify(auditoriaService).registrarAparte(
                    eq(null),
                    eq(AuditoriaService.ACCION_REGISTRO_RECHAZADO_MODERACION),
                    anyString(),
                    eq(request));

            // Lo importante: la moderacion corre ANTES de escribir. Si corriera
            // despues, el texto ofensivo quedaria en la base aunque el registro
            // se rechace.
            verify(personaRepository, never()).save(any());
            verify(usuarioRepository, never()).save(any());
        }

        @Test
        @DisplayName("Se moderan nickname, nombre y apellido")
        void moderaLosTresCamposVisibles() {
            authService.registrar(peticionValida(RolNombre.PARTICIPANTE), request);

            verify(moderacionService).validar("maria_g", "nickname");
            verify(moderacionService).validar("Maria", "nombre");
            verify(moderacionService).validar("Gonzalez", "apellido");
        }
    }

    // =====================================================================
    // Catalogos
    // =====================================================================

    @Nested
    @DisplayName("Catalogos del sistema")
    class Catalogos {

        @Test
        @DisplayName("Sin estado ACTIVO cargado: ReglaNegocioException explicita")
        void fallaSiFaltaElEstadoActivo() {
            when(usuarioEstadoRepository.findByEstadoUsuarioIgnoreCase(anyString()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.registrar(peticionValida(RolNombre.PARTICIPANTE), request))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("catalogo de estados");
        }

        @Test
        @DisplayName("Sin el rol cargado: ReglaNegocioException explicita")
        void fallaSiFaltaElRol() {
            when(rolRepository.findByNombreRolIgnoreCase(anyString()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.registrar(peticionValida(RolNombre.PARTICIPANTE), request))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("no esta cargado en el catalogo");
        }
    }

    // =====================================================================
    // Fabricas de datos de prueba
    // =====================================================================

    private static UsuarioRegistroRequest peticionValida(RolNombre perfil) {
        UsuarioRegistroRequest peticion = new UsuarioRegistroRequest();
        peticion.setEmail("maria.gonzalez@enexia.com");
        peticion.setNickname("maria_g");
        peticion.setPassword("Segura123");
        peticion.setPasswordConfirmacion("Segura123");
        peticion.setNombre("Maria");
        peticion.setApellido("Gonzalez");
        peticion.setDni("40123456");
        peticion.setFechaNacimiento(LocalDate.of(1998, 5, 14));
        peticion.setPerfil(perfil.name());
        return peticion;
    }

    private static Rol rol(Long id, String nombre) {
        Rol rol = new Rol();
        rol.setIdRol(id);
        rol.setNombreRol(nombre);
        return rol;
    }

    private static UsuarioEstado estado(String nombre) {
        UsuarioEstado estado = new UsuarioEstado();
        estado.setIdEstadoUsuario(1L);
        estado.setEstadoUsuario(nombre);
        return estado;
    }
}
