package com.enexia.rg.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.enexia.rg.model.EstadoUsuarioNombre;
import com.enexia.rg.model.HistorialEstadoUsuario;
import com.enexia.rg.model.Usuario;
import com.enexia.rg.model.UsuarioEstado;
import com.enexia.rg.repository.HistorialEstadoUsuarioRepository;
import com.enexia.rg.repository.UsuarioEstadoRepository;
import com.enexia.rg.repository.UsuarioRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Politica de penalizacion por intentos fallidos (DFD Login 1.2.5A a 1.2.7).
 *
 * ESCALA APLICADA
 *   3 fallos -> requiere_captcha = true  + cooldown de 5 minutos
 *   6 fallos -> cooldown de 30 minutos
 *   9 fallos -> estado BLOQUEADO (ya no se levanta solo; lo destraba un admin)
 *
 * Los umbrales salen de application.properties para poder endurecer o relajar
 * la politica sin recompilar.
 *
 * DISCREPANCIA DOCUMENTADA
 * RF-1.4 dice "bloquear al tercer intento fallido". El DFD de login, posterior
 * y mas detallado, define la escala de arriba. Se implemento el DFD por
 * decision del 2026-08-24; RF-1.4 deberia actualizarse para reflejarlo.
 *
 * POR QUE ES UNA CLASE APARTE Y NO METODOS DE AuthService
 * Estos metodos necesitan {@code REQUIRES_NEW}, y las anotaciones
 * transaccionales de Spring funcionan por proxy: solo se aplican cuando la
 * llamada entra desde afuera del bean. Si estos metodos vivieran dentro de
 * AuthService y se invocaran como {@code this.registrarFallo(...)}, la llamada
 * no pasaria por el proxy y {@code @Transactional} quedaria sin efecto,
 * silenciosamente. Separarlos en otro bean es lo que garantiza que la
 * anotacion realmente se aplique.
 */
@Service
@Slf4j
public class IntentosLoginService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioEstadoRepository usuarioEstadoRepository;
    private final HistorialEstadoUsuarioRepository historialEstadoRepository;

    private final int umbralCaptcha;
    private final int umbralCooldownLargo;
    private final int umbralBloqueo;
    private final int cooldownCortoMinutos;
    private final int cooldownLargoMinutos;

    public IntentosLoginService(
            UsuarioRepository usuarioRepository,
            UsuarioEstadoRepository usuarioEstadoRepository,
            HistorialEstadoUsuarioRepository historialEstadoRepository,
            @Value("${enexia.security.login.intentos-captcha}") int umbralCaptcha,
            @Value("${enexia.security.login.intentos-cooldown-largo}") int umbralCooldownLargo,
            @Value("${enexia.security.login.intentos-bloqueo}") int umbralBloqueo,
            @Value("${enexia.security.login.cooldown-corto-minutos}") int cooldownCortoMinutos,
            @Value("${enexia.security.login.cooldown-largo-minutos}") int cooldownLargoMinutos) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioEstadoRepository = usuarioEstadoRepository;
        this.historialEstadoRepository = historialEstadoRepository;
        this.umbralCaptcha = umbralCaptcha;
        this.umbralCooldownLargo = umbralCooldownLargo;
        this.umbralBloqueo = umbralBloqueo;
        this.cooldownCortoMinutos = cooldownCortoMinutos;
        this.cooldownLargoMinutos = cooldownLargoMinutos;
    }

    /**
     * Contabiliza un intento fallido y aplica la penalizacion que corresponda.
     *
     * REQUIRES_NEW es imprescindible: quien llama a este metodo va a lanzar
     * CredencialesInvalidasException inmediatamente despues. Si compartieran
     * transaccion, Spring la revertiria al propagarse la excepcion y el
     * incremento del contador se perderia. El resultado seria un contador que
     * nunca avanza y una cuenta que jamas se bloquea: exactamente la falla que
     * este codigo intenta prevenir.
     *
     * @return true si el intento dejo la cuenta BLOQUEADA
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean registrarFallo(Long idUsuario) {

        // Lectura con bloqueo de fila (SELECT ... FOR UPDATE). El contador es un
        // leer-modificar-escribir: sin el bloqueo, dos intentos simultaneos leen
        // el mismo valor y uno de los incrementos se pierde, permitiendo pasar
        // los umbrales sin ser penalizado.
        Optional<Usuario> encontrado = usuarioRepository.bloquearParaActualizarSeguridad(idUsuario);
        if (encontrado.isEmpty()) {
            return false;
        }
        Usuario usuario = encontrado.get();

        int intentos = (usuario.getIntentosFallidos() == null ? 0 : usuario.getIntentosFallidos()) + 1;
        usuario.setIntentosFallidos(intentos);

        boolean quedaBloqueada = false;

        if (intentos >= umbralBloqueo) {
            aplicarBloqueo(usuario);
            quedaBloqueada = true;
            log.warn("Cuenta {} BLOQUEADA tras {} intentos fallidos", idUsuario, intentos);

        } else if (intentos >= umbralCooldownLargo) {
            usuario.setFechaDesbloqueoCooldown(LocalDateTime.now().plusMinutes(cooldownLargoMinutos));
            log.warn("Cuenta {} penalizada {} minutos ({} intentos)",
                    idUsuario, cooldownLargoMinutos, intentos);

        } else if (intentos >= umbralCaptcha) {
            usuario.setRequiereCaptcha(true);
            usuario.setFechaDesbloqueoCooldown(LocalDateTime.now().plusMinutes(cooldownCortoMinutos));
            log.warn("Cuenta {} penalizada {} minutos y marcada para captcha ({} intentos)",
                    idUsuario, cooldownCortoMinutos, intentos);
        }

        usuarioRepository.save(usuario);
        return quedaBloqueada;
    }

    /**
     * Limpia los contadores tras un login correcto (DFD Login 1.2.5A).
     *
     * Es tan importante como incrementarlos: sin este reseteo, los fallos se
     * acumularian a lo largo de meses y un usuario legitimo terminaria bloqueado
     * por errores de tipeo ocasionales y sin relacion entre si.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void limpiarTrasLoginExitoso(Long idUsuario) {
        usuarioRepository.bloquearParaActualizarSeguridad(idUsuario).ifPresent(usuario -> {
            usuario.setIntentosFallidos(0);
            usuario.setRequiereCaptcha(false);
            usuario.setFechaDesbloqueoCooldown(null);
            usuarioRepository.save(usuario);
        });
    }

    /** Cambia el estado a BLOQUEADO y lo asienta en el historial de estados. */
    private void aplicarBloqueo(Usuario usuario) {
        Optional<UsuarioEstado> bloqueado =
                usuarioEstadoRepository.findByEstadoUsuarioIgnoreCase(EstadoUsuarioNombre.BLOQUEADO.name());

        if (bloqueado.isEmpty()) {
            // El catalogo de estados deberia venir precargado por DatosInicialesConfig.
            // Si falta, se avisa fuerte pero no se corta: perder la penalizacion de
            // cooldown por un problema de datos maestros seria peor.
            log.error("El catalogo usuario_estado no tiene la fila BLOQUEADO. "
                    + "No se pudo bloquear la cuenta {}.", usuario.getIdUsuario());
            return;
        }

        usuario.setEstadoUsuario(bloqueado.get());

        // El MER exige trazabilidad de cada cambio de estado (tabla
        // historial_estado_usuario): permite auditar despues quien quedo
        // bloqueado y cuando.
        HistorialEstadoUsuario historial = new HistorialEstadoUsuario();
        historial.setUsuario(usuario);
        historial.setEstadoUsuario(bloqueado.get());
        historial.setEstadoUsuarioSistema(usuario.getEstadoUsuarioSistema());
        historial.setFechaCambio(LocalDateTime.now());
        historialEstadoRepository.save(historial);
    }
}
