package com.enexia.rg.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.enexia.rg.exception.RateLimitExcedidoException;
import com.enexia.rg.repository.HistorialInteraccionesRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Control de trafico por direccion IP (DFD Login 1.2.1).
 *
 * QUE PROBLEMA RESUELVE, DISTINTO DEL BLOQUEO DE CUENTA
 * El bloqueo por intentos fallidos protege UNA cuenta: nueve fallos sobre el
 * mismo email y esa cuenta queda bloqueada. Pero no frena al atacante que
 * prueba una contrasena comun contra diez mil emails distintos ("password
 * spraying"): ninguna cuenta llega a tres fallos, y el ataque pasa entero por
 * debajo del radar.
 *
 * Este control mira el otro eje: cuantos intentos hizo una misma IP, sin
 * importar contra que cuentas. Los dos controles son complementarios y hacen
 * falta ambos.
 *
 * IMPLEMENTACION Y SU LIMITE
 * El conteo sale de {@code historial_interacciones}, o sea que cada
 * verificacion es una consulta a la base. Es simple y sobra para el MVP, pero
 * bajo carga alta conviene mover el contador a memoria (Bucket4j) o a Redis si
 * se despliegan varias instancias.
 *
 * Es una ventana deslizante: se cuentan los intentos de los ultimos N minutos
 * contados hacia atras desde ahora, no los de un bloque horario fijo. Una
 * ventana fija permitiria agotar el cupo al final de un bloque y volver a
 * agotarlo al principio del siguiente, duplicando el limite real.
 */
@Service
@Slf4j
public class RateLimitService {

    private final HistorialInteraccionesRepository historialRepository;
    private final int maxIntentosPorIp;
    private final int ventanaMinutos;

    public RateLimitService(
            HistorialInteraccionesRepository historialRepository,
            @Value("${enexia.security.rate-limit.max-intentos-por-ip}") int maxIntentosPorIp,
            @Value("${enexia.security.rate-limit.ventana-minutos}") int ventanaMinutos) {
        this.historialRepository = historialRepository;
        this.maxIntentosPorIp = maxIntentosPorIp;
        this.ventanaMinutos = ventanaMinutos;
    }

    /**
     * Primer control del login: corta antes de tocar la tabla usuario.
     *
     * El orden importa. Verificar esto antes de buscar al usuario y antes de
     * comparar el hash BCrypt significa que una IP abusiva no consume ni una
     * consulta pesada ni los ~250ms de CPU que cuesta cada verificacion de
     * contrasena. De lo contrario el propio mecanismo de defensa se convierte
     * en el vector de denegacion de servicio.
     *
     * @throws RateLimitExcedidoException si la IP supero el cupo de la ventana
     */
    @Transactional(readOnly = true)
    public void verificarLimiteLogin(String ip) {
        if (ip == null || ip.isBlank()) {
            return;
        }

        LocalDateTime desde = LocalDateTime.now().minusMinutes(ventanaMinutos);
        long intentos = historialRepository.contarPorIpYAccionDesde(
                ip, AuditoriaService.ACCION_LOGIN_FALLIDO, desde);

        if (intentos >= maxIntentosPorIp) {
            log.warn("Rate limit superado: la IP {} acumula {} intentos fallidos en {} minutos",
                    ip, intentos, ventanaMinutos);
            throw new RateLimitExcedidoException();
        }
    }
}
