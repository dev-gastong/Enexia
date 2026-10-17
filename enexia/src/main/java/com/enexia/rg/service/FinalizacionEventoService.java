package com.enexia.rg.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.enexia.rg.exception.ReglaNegocioException;
import com.enexia.rg.model.EstadoEventoOrganizadorNombre;
import com.enexia.rg.model.Evento;
import com.enexia.rg.model.EventoEstadoOrganizador;
import com.enexia.rg.model.HistorialEstadoEvento;
import com.enexia.rg.repository.EventoEstadoOrganizadorRepository;
import com.enexia.rg.repository.EventoRepository;
import com.enexia.rg.repository.HistorialEstadoEventoRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Pasa a FINALIZADO los eventos cuya ultima funcion ya termino.
 *
 * POR QUE EXISTE
 * El estado FINALIZADO estaba declarado (modelo, catalogo, DTO, panel del
 * organizador) pero nada lo asignaba: un evento de la semana pasada seguia
 * "Publicado" en el panel, con Editar y Dar de baja disponibles, y seguia en la
 * grilla del catalogo publico. Se persiste el estado y no se deriva al leer
 * porque lo consumen cuatro lugares distintos (catalogo, panel del organizador,
 * reglas de edicion/baja y panel del administrador) y derivarlo en cada uno
 * garantiza que alguno quede desalineado.
 *
 * LIMITE CONOCIDO
 * Lo ejecuta {@link FinalizacionEventoScheduler} periodicamente, asi que hay un
 * desfase de hasta un intervalo (5 minutos por defecto) entre que termina la
 * ultima funcion y que el estado cambia. Para "no mostrar eventos viejos" es
 * irrelevante; si algun dia hiciera falta cortar al segundo (p. ej. bloquear
 * la baja apenas termina), habria que sumar la comprobacion por fecha en esa
 * regla puntual.
 *
 * ESTRUCTURA
 * {@code idsVencidos} y {@code finalizar} son metodos separados que el
 * scheduler llama desde AFUERA: una transaccion por evento, para que un fallo
 * en uno no deshaga los demas, y para que la transaccion cruce el proxy (ver
 * la nota sobre auto-invocacion de {@code @Transactional} en CLAUDE.md).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinalizacionEventoService {

    private static final String PUBLICADO = EstadoEventoOrganizadorNombre.PUBLICADO.name();

    private final EventoRepository eventoRepository;
    private final EventoEstadoOrganizadorRepository estadoOrganizadorRepository;
    private final HistorialEstadoEventoRepository historialRepository;
    private final AvisoInscriptosService avisoInscriptosService;

    /** Ids de los eventos publicados y aprobados cuya ultima funcion ya paso. */
    @Transactional(readOnly = true)
    public List<Long> idsVencidos(LocalDateTime ahora) {
        return eventoRepository.buscarIdsFinalizables(
                PUBLICADO, EventoRepository.ESTADOS_VISIBLES, null,
                ahora.toLocalDate(), ahora.toLocalTime());
    }

    /**
     * Finaliza un evento. Devuelve false si ya no corresponde (otro proceso lo
     * cambio entre la lectura de ids y ahora).
     *
     * Bloquea la fila y vuelve a comprobar las condiciones DENTRO del bloqueo:
     * entre listar los ids y llegar aca el organizador pudo dar de baja el
     * evento, o un administrador pudo suspenderlo, y esa decision no se pisa.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean finalizar(Long idEvento, LocalDateTime ahora) {
        Evento evento = eventoRepository.bloquearParaActualizar(idEvento).orElse(null);
        if (evento == null) {
            return false;
        }

        boolean sigueVencido = eventoRepository.buscarIdsFinalizables(
                        PUBLICADO, EventoRepository.ESTADOS_VISIBLES, idEvento,
                        ahora.toLocalDate(), ahora.toLocalTime())
                .contains(idEvento);
        if (!sigueVencido) {
            return false;
        }

        EventoEstadoOrganizador finalizado = estadoOrganizadorRepository
                .findByEstadoOrganizadorIgnoreCase(EstadoEventoOrganizadorNombre.FINALIZADO.name())
                .orElseThrow(() -> new ReglaNegocioException(
                        "El catalogo evento_estado_organizador no tiene la fila FINALIZADO"));

        evento.setEstadoOrganizador(finalizado);
        eventoRepository.save(evento);

        HistorialEstadoEvento historial = new HistorialEstadoEvento();
        historial.setEvento(evento);
        historial.setEstadoSistema(evento.getEstadoSistema());
        historial.setEstadoOrganizador(finalizado);
        // usuario null = lo cambio el sistema, no una persona (mismo criterio
        // que el pipeline de moderacion).
        historial.setUsuario(null);
        historial.setFechaCambio(ahora);
        historialRepository.save(historial);

        // RF-3.4: se invita a valorar a quienes tenian lugar confirmado. Sale
        // despues del commit; si otro camino de finalizacion ya lo hizo, la
        // idempotencia por (usuario, evento) evita el segundo correo.
        avisoInscriptosService.invitarAValorar(idEvento, evento.getNombre());

        log.info("Evento {} finalizado automaticamente (ultima funcion ya termino)", idEvento);
        return true;
    }
}
