package com.enexia.rg.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.enexia.rg.exception.ReglaNegocioException;
import com.enexia.rg.model.EstadoUsuarioNombre;
import com.enexia.rg.model.NotificacionTipo;
import com.enexia.rg.model.PasswordResetToken;
import com.enexia.rg.model.Usuario;
import com.enexia.rg.model.UsuarioEstado;
import com.enexia.rg.repository.PasswordResetTokenRepository;
import com.enexia.rg.repository.UsuarioRepository;

/**
 * Pruebas de la recuperacion de acceso (RF-1.5) y del aviso de cuentas
 * bloqueadas por intentos fallidos (RF-1.4). El desbloqueo NO es de este
 * servicio: es exclusivo del panel de administracion.
 *
 * Este servicio es la CONTRAPARTE de la respuesta HTTP uniforme del login: como
 * el login ya no le dice a nadie que la cuenta quedo bloqueada, el email es el
 * unico canal por el que se entera el titular. Si esta pieza falla, la politica
 * de bloqueo silencioso deja al usuario legitimo sin salida.
 *
 * Se prueban las cuatro propiedades de seguridad del token:
 *   1. En la base se guarda el HASH, nunca el token que viaja por mail.
 *   2. Un solo uso: al consumirse, desaparece.
 *   3. Vencido = invalido, y con el MISMO mensaje que uno inexistente.
 *   4. Cambiar la contrasena jamas toca el estado de la cuenta (ni BLOQUEADA ni SUSPENDIDA).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RecuperacionCuentaService - enlace de un solo uso (RF-1.4, RF-1.5)")
class RecuperacionCuentaServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;
    @Mock private NotificacionService notificaciones;
    @Mock private PlatformTransactionManager transactionManager;

    private RecuperacionCuentaService servicio;

    private static final String EMAIL = "ana@enexia.test";
    private static final String PASSWORD_NUEVA = "NuevaClave123";

    @BeforeEach
    void prepararEscenario() {
        // Constructor explicito por el @Value: se instancia a mano en vez de con
        // @InjectMocks para poder fijar la vigencia.
        //
        // El ejecutor corre en el mismo hilo (Runnable::run): en produccion la
        // solicitud se procesa en otro hilo para que la respuesta no dependa de
        // si la cuenta existe, y aca eso solo estorbaria para verificar.
        Executor sincrono = Runnable::run;
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        servicio = new RecuperacionCuentaService(
                usuarioRepository, tokenRepository, passwordEncoder, emailService, notificaciones,
                sincrono, new TransactionTemplate(transactionManager), 30);

        when(notificaciones.reservarConLimite(any(), anyLong(), anyInt(), any(), any()))
                .thenReturn(Optional.of(1L));

        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hashNuevo");
        when(tokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    // =====================================================================
    // Emision
    // =====================================================================

    @Nested
    @DisplayName("Emision del enlace")
    class Emision {

        @Test
        @DisplayName("El aviso de bloqueo emite token y manda el correo")
        void avisoDeBloqueo() {
            when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario(EstadoUsuarioNombre.BLOQUEADO)));

            servicio.notificarBloqueoConEnlaceDeRecuperacion(1L);

            verify(tokenRepository).save(any());
            verify(emailService).enviarAvisoBloqueo(anyString(), anyString());
        }

        @Test
        @DisplayName("En la base se guarda el HASH, no el token del correo")
        void guardaHashNoToken() {
            when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario(EstadoUsuarioNombre.BLOQUEADO)));
            ArgumentCaptor<PasswordResetToken> guardado =
                    ArgumentCaptor.forClass(PasswordResetToken.class);
            ArgumentCaptor<String> enviado = ArgumentCaptor.forClass(String.class);

            servicio.notificarBloqueoConEnlaceDeRecuperacion(1L);

            verify(tokenRepository).save(guardado.capture());
            verify(emailService).enviarAvisoBloqueo(anyString(), enviado.capture());

            // Durante su vigencia el token equivale a la contrasena. Si se
            // guardara en claro, un dump o un backup filtrado entregaria acceso
            // inmediato a toda cuenta con recuperacion pendiente.
            assertThat(guardado.getValue().getToken()).isNotEqualTo(enviado.getValue());
            // SHA-256 en hexadecimal: 64 caracteres.
            assertThat(guardado.getValue().getToken()).hasSize(64);
        }

        @Test
        @DisplayName("Se invalidan los tokens previos antes de emitir uno nuevo")
        void invalidaAnteriores() {
            when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario(EstadoUsuarioNombre.BLOQUEADO)));

            servicio.notificarBloqueoConEnlaceDeRecuperacion(1L);

            // Si convivieran varios vigentes, un enlace viejo filtrado seguiria
            // sirviendo despues de que el usuario pidiera otro.
            verify(tokenRepository).borrarPorUsuario(1L);
        }

        @Test
        @DisplayName("El token vence a los 30 minutos configurados")
        void vigenciaConfigurada() {
            when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario(EstadoUsuarioNombre.BLOQUEADO)));
            ArgumentCaptor<PasswordResetToken> captor =
                    ArgumentCaptor.forClass(PasswordResetToken.class);

            servicio.notificarBloqueoConEnlaceDeRecuperacion(1L);

            verify(tokenRepository).save(captor.capture());
            assertThat(captor.getValue().getFechaExpiracion())
                    .isAfter(LocalDateTime.now().plusMinutes(29))
                    .isBefore(LocalDateTime.now().plusMinutes(31));
        }

        @Test
        @DisplayName("Email no registrado: no emite nada y no lanza (no revela existencia)")
        void emailInexistente() {
            when(usuarioRepository.buscarActivoPorEmailConRoles(anyString())).thenReturn(Optional.empty());

            servicio.solicitarRestablecimiento("desconocido@enexia.test");

            // El controller responde siempre lo mismo. Un "ese email no existe"
            // convertiria este endpoint publico en un enumerador de cuentas mas
            // comodo todavia que el login.
            verify(tokenRepository, never()).save(any());
            verify(emailService, never()).enviarRecuperacionPassword(anyString(), anyString());
        }

        @Test
        @DisplayName("Email registrado: emite el token y manda el enlace")
        void emailRegistrado() {
            when(usuarioRepository.buscarActivoPorEmailConRoles(EMAIL))
                    .thenReturn(Optional.of(usuario(EstadoUsuarioNombre.ACTIVO)));

            servicio.solicitarRestablecimiento(EMAIL);

            verify(tokenRepository).save(any());
            verify(emailService).enviarRecuperacionPassword(anyString(), anyString());
        }

        @Test
        @DisplayName("Limite por destinatario alcanzado: no emite token ni manda correo")
        void limitePorDestinatario() {
            when(usuarioRepository.buscarActivoPorEmailConRoles(EMAIL))
                    .thenReturn(Optional.of(usuario(EstadoUsuarioNombre.ACTIVO)));
            when(notificaciones.reservarConLimite(any(), anyLong(), anyInt(), any(), any()))
                    .thenReturn(Optional.empty());

            servicio.solicitarRestablecimiento(EMAIL);

            // Sin este tope, cualquiera puede llenar la casilla de otra persona
            // y gastar la cuota diaria del proveedor pidiendo enlaces en bucle.
            // Se cuenta por cuenta, no por IP (el limite por IP se retiro por el CGNAT).
            verify(notificaciones).reservarConLimite(
                    org.mockito.ArgumentMatchers.eq(NotificacionTipo.RECUPERACION),
                    anyLong(), anyInt(), any(), any());
            verify(tokenRepository, never()).save(any());
            verify(emailService, never()).enviarRecuperacionPassword(anyString(), anyString());
        }

        @Test
        @DisplayName("Un fallo interno no escapa: el endpoint no puede responder distinto")
        void falloInternoNoEscapa() {
            when(usuarioRepository.buscarActivoPorEmailConRoles(EMAIL))
                    .thenThrow(new IllegalStateException("base caida"));

            // Si lanzara, la respuesta seria un 500 y no el 202 uniforme: otra
            // forma de distinguir un caso de otro.
            servicio.solicitarRestablecimiento(EMAIL);

            verify(emailService, never()).enviarRecuperacionPassword(anyString(), anyString());
        }
    }

    // =====================================================================
    // Consumo
    // =====================================================================

    @Nested
    @DisplayName("Consumo del enlace")
    class Consumo {

        @Test
        @DisplayName("Token valido: cambia la contrasena, limpia el contador y consume el token")
        void restablecimientoCorrecto() {
            Usuario usuario = usuario(EstadoUsuarioNombre.ACTIVO);
            usuario.setIntentosFallidos(2);

            PasswordResetToken token = tokenVigente(usuario);
            when(tokenRepository.buscarPorHashConUsuario(anyString())).thenReturn(Optional.of(token));

            servicio.confirmarRestablecimiento("tokenPlano", PASSWORD_NUEVA, PASSWORD_NUEVA);

            assertThat(usuario.getPassword()).isEqualTo("$2a$12$hashNuevo");
            // Quien acaba de probar que controla la casilla no debe arrastrar la
            // penalizacion que dejo el atacante.
            assertThat(usuario.getIntentosFallidos()).isZero();
            // Un solo uso: si el enlace queda en el historial del navegador o se
            // reenvia, ya no sirve.
            verify(tokenRepository).delete(token);
        }

        @Test
        @DisplayName("Cambiada la contrasena, avisa al titular por correo")
        void avisaContrasenaCambiada() {
            Usuario usuario = usuario(EstadoUsuarioNombre.ACTIVO);
            when(tokenRepository.buscarPorHashConUsuario(anyString()))
                    .thenReturn(Optional.of(tokenVigente(usuario)));

            servicio.confirmarRestablecimiento("tokenPlano", PASSWORD_NUEVA, PASSWORD_NUEVA);

            // Si alguien tomo la cuenta, el titular se entera por la unica
            // casilla que el atacante no controla.
            verify(emailService).enviarPasswordCambiada(usuario.getEmail());
        }

        @Test
        @DisplayName("Token invalido: no se avisa de ningun cambio")
        void sinAvisoSiFalla() {
            when(tokenRepository.buscarPorHashConUsuario(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    servicio.confirmarRestablecimiento("inventado", PASSWORD_NUEVA, PASSWORD_NUEVA))
                    .isInstanceOf(ReglaNegocioException.class);

            verify(emailService, never()).enviarPasswordCambiada(anyString());
        }

        @Test
        @DisplayName("Cuenta BLOQUEADA: cambia la contrasena pero NO se desbloquea (solo el admin puede)")
        void noDesbloqueaCuentaBloqueada() {
            Usuario usuario = usuario(EstadoUsuarioNombre.BLOQUEADO);
            when(tokenRepository.buscarPorHashConUsuario(anyString()))
                    .thenReturn(Optional.of(tokenVigente(usuario)));

            servicio.confirmarRestablecimiento("tokenPlano", PASSWORD_NUEVA, PASSWORD_NUEVA);

            // La clave si cambia (el titular puede renovarla si sospecha de un intruso)...
            assertThat(usuario.getPassword()).isEqualTo("$2a$12$hashNuevo");
            // ...pero el estado queda como estaba: el desbloqueo es del panel de administracion.
            assertThat(usuario.getEstadoUsuario().getEstadoUsuario())
                    .isEqualTo(EstadoUsuarioNombre.BLOQUEADO.name());
        }

        @Test
        @DisplayName("Cuenta SUSPENDIDA: cambia la contrasena pero NO levanta la sancion")
        void noLevantaSuspension() {
            Usuario usuario = usuario(EstadoUsuarioNombre.SUSPENDIDO);
            when(tokenRepository.buscarPorHashConUsuario(anyString()))
                    .thenReturn(Optional.of(tokenVigente(usuario)));

            servicio.confirmarRestablecimiento("tokenPlano", PASSWORD_NUEVA, PASSWORD_NUEVA);

            // Si cambiar la contrasena levantara una suspension, cualquier
            // sancionado la esquivaria pidiendo un restablecimiento.
            assertThat(usuario.getEstadoUsuario().getEstadoUsuario())
                    .isEqualTo(EstadoUsuarioNombre.SUSPENDIDO.name());
        }

        @Test
        @DisplayName("Token inexistente y token vencido dan el MISMO mensaje")
        void mensajeUnicoParaTokenInvalido() {
            when(tokenRepository.buscarPorHashConUsuario(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    servicio.confirmarRestablecimiento("inventado", PASSWORD_NUEVA, PASSWORD_NUEVA))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("no es valido o ya fue utilizado");

            Usuario usuario = usuario(EstadoUsuarioNombre.ACTIVO);
            PasswordResetToken vencido = tokenVigente(usuario);
            vencido.setFechaExpiracion(LocalDateTime.now().minusMinutes(1));
            when(tokenRepository.buscarPorHashConUsuario(anyString())).thenReturn(Optional.of(vencido));

            // Mismo texto a proposito: no hay razon para ayudar a distinguir un
            // token inventado de uno que caduco.
            assertThatThrownBy(() ->
                    servicio.confirmarRestablecimiento("vencido", PASSWORD_NUEVA, PASSWORD_NUEVA))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("no es valido o ya fue utilizado");
        }

        @Test
        @DisplayName("Contrasenas que no coinciden: corta antes de consultar la base")
        void passwordsDistintas() {
            assertThatThrownBy(() ->
                    servicio.confirmarRestablecimiento("token", PASSWORD_NUEVA, "OtraClave123"))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("no coinciden");

            verify(tokenRepository, never()).buscarPorHashConUsuario(anyString());
        }
    }

    // =====================================================================
    // Auxiliares
    // =====================================================================

    private Usuario usuario(EstadoUsuarioNombre nombreEstado) {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(1L);
        usuario.setEmail(EMAIL);
        usuario.setPassword("$2a$12$hashViejo");
        usuario.setEstadoUsuario(estado(nombreEstado));
        return usuario;
    }

    private UsuarioEstado estado(EstadoUsuarioNombre nombre) {
        UsuarioEstado estado = new UsuarioEstado();
        estado.setEstadoUsuario(nombre.name());
        return estado;
    }

    private PasswordResetToken tokenVigente(Usuario usuario) {
        PasswordResetToken token = new PasswordResetToken();
        token.setIdToken(1L);
        token.setUsuario(usuario);
        token.setToken("hash-cualquiera");
        token.setFechaExpiracion(LocalDateTime.now().plusMinutes(20));
        return token;
    }
}
