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
 * Politica de penalizacion por intentos fallidos (RF-1.4, DFD Login 1.2.4-1.2.5).
 *
 * REGLA UNICA (ADR-13, 2026-09-09)
 *   3 fallos consecutivos -> estado_usuario = BLOQUEADO.
 *
 * No hay CAPTCHA ni cooldown intermedio: la escalera 3/6/9 usada hasta el
 * 2026-09-19 se descarto por decision del usuario. El bloqueo no se informa
 * por ningun canal visible para quien esta logueandose (ver AuthService); el
 * aviso llega solo por email, con el enlace de recuperacion que tambien
 * reactiva la cuenta (RecuperacionCuentaService).
 *
 * El umbral sale de application.properties para poder endurecer o relajar la
 * politica sin recompilar.
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

    private final int umbralBloqueo;

    public IntentosLoginService(
            UsuarioRepository usuarioRepository,
            UsuarioEstadoRepository usuarioEstadoRepository,
            HistorialEstadoUsuarioRepository historialEstadoRepository,
            @Value("${enexia.security.login.intentos-bloqueo}") int umbralBloqueo) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioEstadoRepository = usuarioEstadoRepository;
        this.historialEstadoRepository = historialEstadoRepository;
        this.umbralBloqueo = umbralBloqueo;
    }

    /**
     * Contabiliza un intento fallido y bloquea la cuenta si llego al umbral.
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
        // el umbral sin ser penalizado.
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
        }

        usuarioRepository.save(usuario);
        return quedaBloqueada;
    }

    /**
     * Limpia el contador tras un login correcto (DFD Login 1.2.5A).
     *
     * Es tan importante como incrementarlo: sin este reseteo, los fallos se
     * acumularian a lo largo de meses y un usuario legitimo terminaria bloqueado
     * por errores de tipeo ocasionales y sin relacion entre si.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void limpiarTrasLoginExitoso(Long idUsuario) {
        usuarioRepository.bloquearParaActualizarSeguridad(idUsuario).ifPresent(usuario -> {
            usuario.setIntentosFallidos(0);
            usuarioRepository.save(usuario);
        });
    }

    /** Cambia el estado a BLOQUEADO y lo asienta en el historial de estados. */
    private void aplicarBloqueo(Usuario usuario) {
        Optional<UsuarioEstado> bloqueado =
                usuarioEstadoRepository.findByEstadoUsuarioIgnoreCase(EstadoUsuarioNombre.BLOQUEADO.name());

        if (bloqueado.isEmpty()) {
            // El catalogo de estados deberia venir precargado por DatosInicialesConfig.
            // Si falta, se avisa fuerte pero no se corta: perder el bloqueo por un
            // problema de datos maestros seria peor.
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
