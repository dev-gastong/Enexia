package com.enexia.rg.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.enexia.rg.exception.ReglaNegocioException;
import com.enexia.rg.model.EstadoUsuarioNombre;
import com.enexia.rg.model.HistorialEstadoUsuario;
import com.enexia.rg.model.PasswordResetToken;
import com.enexia.rg.model.Usuario;
import com.enexia.rg.model.UsuarioEstado;
import com.enexia.rg.repository.HistorialEstadoUsuarioRepository;
import com.enexia.rg.repository.PasswordResetTokenRepository;
import com.enexia.rg.repository.UsuarioEstadoRepository;
import com.enexia.rg.repository.UsuarioRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Recuperacion de acceso por correo (RF-1.5) y desbloqueo de cuentas bloqueadas
 * por intentos fallidos (RF-1.4).
 *
 * PIEZA CENTRAL DE LA POLITICA DE BLOQUEO SILENCIOSO (2026-09-08)
 * Desde que el login responde 401 generico ante una cuenta bloqueada, el titular
 * ya no puede enterarse por la pantalla. Este servicio es el canal que lo
 * reemplaza: al dispararse el bloqueo se emite un token y se manda por email a
 * la casilla registrada. El atacante no la controla, asi que la informacion
 * llega solo a quien corresponde.
 *
 * POR QUE SE GUARDA EL HASH DEL TOKEN Y NO EL TOKEN
 * El token es, durante su vigencia, equivalente a la contrasena: quien lo tenga
 * puede tomar la cuenta. Si se guardara en claro, una lectura de la tabla (dump,
 * backup filtrado, inyeccion SQL en otro modulo) entregaria acceso inmediato a
 * todas las cuentas con recuperacion pendiente. Guardando SHA-256, lo que se
 * filtra es inservible: no se puede invertir para reconstruir el enlace.
 *
 * No se usa BCrypt aca (si para las contrasenas) porque el token ya es aleatorio
 * de 256 bits: no hay diccionario que probar, y BCrypt solo agregaria ~250ms por
 * verificacion sin ninguna ganancia real de seguridad.
 */
@Service
@Slf4j
public class RecuperacionCuentaService {

    /** 32 bytes = 256 bits de entropia. Inadivinables por fuerza bruta. */
    private static final int BYTES_TOKEN = 32;

    private static final SecureRandom ALEATORIO = new SecureRandom();

    /** Mismo texto para token inexistente, vencido o ya usado. Ver confirmarRestablecimiento(). */
    private static final String ENLACE_INVALIDO =
            "El enlace de recuperacion no es valido o ya fue utilizado";

    private final UsuarioRepository usuarioRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final UsuarioEstadoRepository usuarioEstadoRepository;
    private final HistorialEstadoUsuarioRepository historialEstadoRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final int vigenciaMinutos;

    public RecuperacionCuentaService(
            UsuarioRepository usuarioRepository,
            PasswordResetTokenRepository tokenRepository,
            UsuarioEstadoRepository usuarioEstadoRepository,
            HistorialEstadoUsuarioRepository historialEstadoRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            @Value("${enexia.security.recuperacion.vigencia-minutos}") int vigenciaMinutos) {
        this.usuarioRepository = usuarioRepository;
        this.tokenRepository = tokenRepository;
        this.usuarioEstadoRepository = usuarioEstadoRepository;
        this.historialEstadoRepository = historialEstadoRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.vigenciaMinutos = vigenciaMinutos;
    }

    // =====================================================================
    // EMISION
    // =====================================================================

    /**
     * Emite un token y manda el aviso de bloqueo automatico (RF-1.4 + RF-1.5).
     *
     * REQUIRES_NEW por el mismo motivo que IntentosLoginService: quien llama va
     * a lanzar la excepcion de login inmediatamente despues, y si compartieran
     * transaccion el token se perderia al revertirse. El usuario recibiria un
     * email con un enlace que no existe en la base.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notificarBloqueoConEnlaceDeRecuperacion(Long idUsuario) {
        usuarioRepository.findById(idUsuario).ifPresent(usuario -> {
            String tokenPlano = emitirToken(usuario);
            emailService.enviarAvisoBloqueo(usuario.getEmail(), tokenPlano);
            log.info("Aviso de bloqueo emitido para el usuario {}", idUsuario);
        });
    }

    /**
     * Restablecimiento pedido por el propio usuario (RF-1.5).
     *
     * NO informa si el email existe: el controller responde siempre lo mismo.
     * Un "ese email no esta registrado" convertiria este endpoint publico en un
     * enumerador de cuentas mas comodo todavia que el login.
     */
    @Transactional
    public void solicitarRestablecimiento(String email) {
        Optional<Usuario> encontrado = usuarioRepository.buscarActivoPorEmailConRoles(email);

        if (encontrado.isEmpty()) {
            // Se deja rastro del lado del servidor y se corta en silencio.
            log.info("Solicitud de restablecimiento para un email no registrado");
            return;
        }

        Usuario usuario = encontrado.get();
        String tokenPlano = emitirToken(usuario);
        emailService.enviarRecuperacionPassword(usuario.getEmail(), tokenPlano);
    }

    /** Crea el token, guarda su hash y devuelve el valor que viaja por email. */
    private String emitirToken(Usuario usuario) {
        // Invalida los anteriores: solo puede haber un enlace vigente por cuenta.
        tokenRepository.borrarPorUsuario(usuario.getIdUsuario());

        byte[] bytes = new byte[BYTES_TOKEN];
        ALEATORIO.nextBytes(bytes);
        // Base64 URL-safe y sin relleno: el token viaja en una query string y no
        // debe traer '+', '/' ni '=' que obliguen a escaparlo.
        String tokenPlano = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        PasswordResetToken registro = new PasswordResetToken();
        registro.setUsuario(usuario);
        registro.setToken(hashear(tokenPlano));
        registro.setFechaExpiracion(LocalDateTime.now().plusMinutes(vigenciaMinutos));
        tokenRepository.save(registro);

        return tokenPlano;
    }

    // =====================================================================
    // CONSUMO
    // =====================================================================

    /**
     * Consume el token, fija la contrasena nueva y, si la cuenta estaba
     * BLOQUEADA por intentos fallidos, la reactiva.
     *
     * QUE ESTADOS SE REACTIVAN Y CUALES NO
     * Solo BLOQUEADO, porque es un estado que puso el sistema y que el titular
     * legitimo tiene derecho a revertir. SUSPENDIDO (sancion de un
     * administrador) y DE_BAJA (baja voluntaria) NO se tocan: si cambiar la
     * contrasena levantara una suspension, cualquier sancionado la esquivaria
     * pidiendo un restablecimiento.
     */
    @Transactional
    public void confirmarRestablecimiento(String tokenPlano, String passwordNueva,
                                          String passwordConfirmacion) {

        if (!passwordNueva.equals(passwordConfirmacion)) {
            throw new ReglaNegocioException("Las contrasenas no coinciden");
        }

        PasswordResetToken registro = tokenRepository
                .buscarPorHashConUsuario(hashear(tokenPlano))
                // Mismo mensaje para "no existe" y para "vencido": no hay razon
                // para ayudar a distinguir un token inventado de uno caducado.
                .orElseThrow(() -> new ReglaNegocioException(ENLACE_INVALIDO));

        if (registro.getFechaExpiracion() == null
                || registro.getFechaExpiracion().isBefore(LocalDateTime.now())) {
            // Se borra igual: un token vencido no tiene por que seguir ocupando fila.
            tokenRepository.delete(registro);
            throw new ReglaNegocioException(ENLACE_INVALIDO);
        }

        Usuario usuario = registro.getUsuario();
        usuario.setPassword(passwordEncoder.encode(passwordNueva));

        // Los contadores se limpian siempre: quien acaba de probar que controla
        // la casilla no debe arrastrar la penalizacion que dejo el atacante.
        usuario.setIntentosFallidos(0);
        usuario.setRequiereCaptcha(false);
        usuario.setFechaDesbloqueoCooldown(null);

        reactivarSiEstabaBloqueada(usuario);

        usuarioRepository.save(usuario);

        // Un solo uso: el token desaparece apenas se consume. Si el enlace se
        // reenvia o queda en el historial del navegador, ya no sirve.
        tokenRepository.delete(registro);

        log.info("Contrasena restablecida para el usuario {}", usuario.getIdUsuario());
    }

    private void reactivarSiEstabaBloqueada(Usuario usuario) {
        UsuarioEstado estadoActual = usuario.getEstadoUsuario();
        String nombre = estadoActual != null ? estadoActual.getEstadoUsuario() : null;

        if (!EstadoUsuarioNombre.BLOQUEADO.name().equalsIgnoreCase(nombre)) {
            return;
        }

        Optional<UsuarioEstado> activo = usuarioEstadoRepository
                .findByEstadoUsuarioIgnoreCase(EstadoUsuarioNombre.ACTIVO.name());

        if (activo.isEmpty()) {
            log.error("El catalogo usuario_estado no tiene la fila ACTIVO. "
                    + "La cuenta {} queda BLOQUEADA pese al restablecimiento.", usuario.getIdUsuario());
            return;
        }

        usuario.setEstadoUsuario(activo.get());

        HistorialEstadoUsuario historial = new HistorialEstadoUsuario();
        historial.setUsuario(usuario);
        historial.setEstadoUsuario(activo.get());
        historial.setEstadoUsuarioSistema(usuario.getEstadoUsuarioSistema());
        historial.setFechaCambio(LocalDateTime.now());
        historialEstadoRepository.save(historial);

        log.info("Cuenta {} desbloqueada por recuperacion via email", usuario.getIdUsuario());
    }

    /** SHA-256 en hexadecimal. Determinista y sin sal: hay que poder buscarlo. */
    private String hashear(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] resumen = digest.digest(valor.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder(resumen.length * 2);
            for (byte b : resumen) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();

        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 es obligatorio en toda JVM; si falta, algo esta muy mal.
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", ex);
        }
    }
}
