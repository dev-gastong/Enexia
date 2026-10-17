package com.enexia.rg.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.enexia.rg.model.NotificacionEnviada;
import com.enexia.rg.model.NotificacionTipo;
import com.enexia.rg.repository.NotificacionEnviadaRepository;

import lombok.RequiredArgsConstructor;

/**
 * Libro de correos (D7): reserva el derecho a mandar un aviso y registra como
 * termino.
 *
 * REQUIRES_NEW EN TODO
 * Quien llama corre en un hilo de fondo, no dentro de la transaccion de
 * negocio. Igual se fuerza una transaccion propia para que una reserva
 * confirmada no dependa de nada ajeno, y para que un fallo del libro nunca
 * arrastre la operacion que origino el correo.
 *
 * LA RESERVA LANZA ANTE UN DUPLICADO CONCURRENTE
 * Si dos hilos reservan lo mismo a la vez, el segundo choca con el UNIQUE y la
 * excepcion {@code DataIntegrityViolationException} sale por el proxy (la
 * transaccion se revierte limpia). Se deja salir a proposito: capturarla aca
 * adentro dejaria la transaccion marcada rollback-only y el commit fallaria con
 * un error mas confuso. Quien llama ({@link EmailService}) la atrapa afuera.
 */
@Service
@RequiredArgsConstructor
public class NotificacionService {

    private final NotificacionEnviadaRepository repository;

    /**
     * Reserva el aviso. Devuelve el id de la fila, o vacio si ese mismo aviso
     * ya fue reservado antes (idempotencia).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Long> reservar(NotificacionTipo tipo, Long idUsuario, Long idReferencia) {
        if (idReferencia != null
                && repository.existsByTipoAndIdUsuarioAndIdReferencia(tipo.name(), idUsuario, idReferencia)) {
            return Optional.empty();
        }
        return Optional.of(guardar(tipo, idUsuario, idReferencia));
    }

    /**
     * Reserva respetando un tope por destinatario: a lo sumo {@code maximo}
     * avisos del tipo dentro de {@code ventana}, y separados por al menos
     * {@code espera}. Devuelve vacio si no corresponde mandar otro.
     *
     * Bajo concurrencia dos pedidos simultaneos pueden pasar ambos el conteo, de
     * modo que el tope puede excederse en uno. Es un freno al abuso, no una
     * garantia contable, y el costo de cerrar esa ventana (bloqueo de fila sobre
     * una tabla de registro) no lo justifica.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Long> reservarConLimite(NotificacionTipo tipo, Long idUsuario, int maximo,
                                            Duration ventana, Duration espera) {
        LocalDateTime ahora = LocalDateTime.now();

        if (repository.countByTipoAndIdUsuarioAndFechaAfter(
                tipo.name(), idUsuario, ahora.minus(ventana)) >= maximo) {
            return Optional.empty();
        }
        boolean muyReciente = repository
                .findFirstByTipoAndIdUsuarioOrderByFechaDesc(tipo.name(), idUsuario)
                .map(ultima -> ultima.getFecha().isAfter(ahora.minus(espera)))
                .orElse(false);
        if (muyReciente) {
            return Optional.empty();
        }
        return Optional.of(guardar(tipo, idUsuario, null));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarResultado(Long idNotificacion, boolean enviado) {
        repository.findById(idNotificacion).ifPresent(fila -> {
            fila.setEstado(enviado ? NotificacionEnviada.ENVIADO : NotificacionEnviada.FALLIDO);
            repository.save(fila);
        });
    }

    private Long guardar(NotificacionTipo tipo, Long idUsuario, Long idReferencia) {
        NotificacionEnviada fila = new NotificacionEnviada();
        fila.setTipo(tipo.name());
        fila.setIdUsuario(idUsuario);
        fila.setIdReferencia(idReferencia);
        fila.setEstado(NotificacionEnviada.PENDIENTE);
        fila.setFecha(LocalDateTime.now());
        return repository.saveAndFlush(fila).getIdNotificacion();
    }
}
