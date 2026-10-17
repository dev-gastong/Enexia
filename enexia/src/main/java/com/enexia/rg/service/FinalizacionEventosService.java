package com.enexia.rg.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
 * Pasa a FINALIZADO los eventos publicados cuyo cronograma ya termino (RF-2.9).
 *
 * El estado FINALIZADO existia en el catalogo pero ningun proceso lo asignaba:
 * un evento de hace dias seguia figurando PUBLICADO en el panel del
 * organizador y en el catalogo publico. Esto lo cierra de dos maneras:
 *   - un barrido periodico, para que el catalogo publico se limpie solo, y
 *   - una pasada inmediata sobre los eventos del organizador antes de listarlos,
 *     para que su panel nunca muestre un evento vencido como activo.
 *
 * Es una transaccion propia (REQUIRES_NEW) porque la invoca el listado, que es
 * de solo lectura; y bloquea cada fila antes de mutarla, igual que el resto de
 * transiciones de estado del evento, por si el organizador lo da de baja justo
 * en ese instante.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinalizacionEventosService {

    private static final String PUBLICADO = EstadoEventoOrganizadorNombre.PUBLICADO.name();

    private final EventoRepository eventoRepository;
    private final EventoEstadoOrganizadorRepository estadoOrganizadorRepository;
    private final HistorialEstadoEventoRepository historialRepository;
    private final com.enexia.rg.repository.UsuarioRepository usuarioRepository;
    private final AvisoInscriptosService avisoInscriptosService;

    /**
     * Variante por email, para llamarla ANTES de la primera lectura de la
     * transaccion del listado: con REPEATABLE READ la lectura fija el snapshot,
     * y un cambio confirmado despues no se veria en esa misma transaccion.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public int finalizarVencidosDe(String emailOrganizador) {
        return usuarioRepository.buscarActivoPorEmailConRoles(emailOrganizador)
                .map(u -> procesar(u.getIdUsuario()))
                .orElse(0);
    }

    /** @param idOrganizador limita a un organizador; null procesa todos */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public int finalizarVencidos(Long idOrganizador) {
        return procesar(idOrganizador);
    }

    /**
     * Logica comun. Privado a proposito: las dos entradas publicas abren la
     * transaccion a traves del proxy; llamar a un metodo publico de esta misma
     * clase desde adentro la salteaba (autoinvocacion) y el bloqueo de fila
     * fallaba con TransactionRequiredException.
     */
    private int procesar(Long idOrganizador) {
        LocalDateTime ahora = LocalDateTime.now();

        List<Long> ids = eventoRepository.buscarVencidosPublicados(
                PUBLICADO, idOrganizador, ahora.toLocalDate(), ahora.toLocalTime());
        if (ids.isEmpty()) {
            return 0;
        }

        EventoEstadoOrganizador finalizado = estadoOrganizadorRepository
                .findByEstadoOrganizadorIgnoreCase(EstadoEventoOrganizadorNombre.FINALIZADO.name())
                .orElse(null);
        if (finalizado == null) {
            log.error("El catalogo evento_estado_organizador no tiene la fila FINALIZADO");
            return 0;
        }

        int cambiados = 0;
        for (Long id : ids) {
            Evento evento = eventoRepository.bloquearParaActualizar(id).orElse(null);
            // Pudo cambiar entre la consulta y el bloqueo (baja, cancelacion).
            if (evento == null || evento.getEstadoOrganizador() == null
                    || !PUBLICADO.equals(evento.getEstadoOrganizador().getEstadoOrganizador())) {
                continue;
            }

            evento.setEstadoOrganizador(finalizado);
            eventoRepository.save(evento);

            HistorialEstadoEvento historial = new HistorialEstadoEvento();
            historial.setEvento(evento);
            historial.setEstadoSistema(evento.getEstadoSistema());
            historial.setEstadoOrganizador(finalizado);
            historial.setUsuario(null); // lo decide el sistema, no una persona
            historial.setFechaCambio(LocalDateTime.now());
            historialRepository.save(historial);

            // Mismo aviso que FinalizacionEventoService: dos caminos finalizan
            // eventos y los dos tienen que invitar. La clave (usuario, evento)
            // del libro de correos evita que se mande dos veces.
            avisoInscriptosService.invitarAValorar(evento.getIdEvento(), evento.getNombre());
            cambiados++;
        }
        return cambiados;
    }
}
