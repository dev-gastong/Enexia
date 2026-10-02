package com.enexia.rg.service;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.enexia.rg.dto.AdminDecisionEventoRequest;
import com.enexia.rg.dto.EventoResponse;
import com.enexia.rg.exception.OperacionNoPermitidaException;
import com.enexia.rg.exception.RecursoNoEncontradoException;
import com.enexia.rg.exception.ReglaNegocioException;
import com.enexia.rg.model.EstadoEventoSistemaNombre;
import com.enexia.rg.model.Evento;
import com.enexia.rg.model.EventoEstadoSistema;
import com.enexia.rg.model.HistorialEstadoEvento;
import com.enexia.rg.model.MotivoSuspensionAdmin;
import com.enexia.rg.model.Usuario;
import com.enexia.rg.repository.EventoEstadoSistemaRepository;
import com.enexia.rg.repository.EventoRepository;
import com.enexia.rg.repository.HistorialEstadoEventoRepository;
import com.enexia.rg.repository.UsuarioRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Modulo 6: moderacion administrativa general de eventos (RF-6.1).
 *
 * Cubre dos escenarios con el mismo mecanismo (PATCH + decision), distinguidos
 * por el estado VIGENTE del evento al momento de decidir, nunca por un campo
 * aparte que el cliente pudiera mandar mal:
 *
 *   (a) Segunda instancia: el evento esta RECHAZADO_SISTEMA (el pipeline
 *       automatico lo rechazo). El administrador puede revertir a
 *       APROBADO_MANUAL o ratificar el rechazo (queda en RECHAZADO_MANUAL,
 *       motivo_codigo = null, igual que hoy).
 *   (b) Suspension disciplinaria: el evento esta APROBADO_SISTEMA o
 *       APROBADO_MANUAL (ya esta publicado). El administrador lo puede pasar
 *       a RECHAZADO_MANUAL con un motivo de {@link MotivoSuspensionAdmin}, y
 *       mas adelante revertir esa suspension a APROBADO_MANUAL si corresponde.
 *
 * EN_PROCESO queda deliberadamente fuera: el pipeline asincrono todavia no
 * dictamino y el evento no tiene contenido persistido (RF-2.2), no hay nada
 * que aprobar o rechazar todavia.
 *
 * MISMO PATRON DE CONCURRENCIA QUE PublicacionEventoService: bloqueo
 * pesimista ANTES de leer el estado que se va a decidir, y READ_COMMITTED
 * (obligatorio desde MariaDB 11.6, ver Evento.java) para que el bloqueo
 * simplemente espere su turno en vez de abortar por snapshot isolation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminEventoService {

    /** Techo de pagina: esta vista trae todo en un solo lote grande, como mis-eventos.html. */
    public static final int TAMANO_PAGINA_MAXIMO = 100;

    private final EventoRepository eventoRepository;
    private final EventoEstadoSistemaRepository estadoSistemaRepository;
    private final HistorialEstadoEventoRepository historialRepository;
    private final UsuarioRepository usuarioRepository;
    private final EventoService eventoService;
    private final EventoMapper eventoMapper;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public Page<EventoResponse> listarParaModeracion(String texto, Pageable paginado) {
        Page<Evento> pagina = eventoRepository.buscarParaModeracion(
                eventoService.normalizarFiltro(texto), paginado);
        return eventoService.mapearPagina(pagina);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EventoResponse decidir(String emailAdmin, Long idEvento,
                                  AdminDecisionEventoRequest datos, HttpServletRequest request) {

        Usuario administrador = usuarioRepository.buscarActivoPorEmailConRoles(emailAdmin)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro la cuenta"));

        // Bloqueo de fila: el pipeline de moderacion o el propio organizador
        // (dar de baja, editar) pueden estar tocando este evento en paralelo.
        Evento evento = eventoRepository.bloquearParaActualizar(idEvento)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro el evento"));

        String estadoActual = evento.getEstadoSistema() == null
                ? null : evento.getEstadoSistema().getEstadoSistema();

        boolean esAprobar = "APROBAR".equals(datos.getDecision());

        EventoEstadoSistema nuevoEstado = esAprobar
                ? resolverAprobacion(estadoActual)
                : resolverRechazo(estadoActual, datos.getMotivoCodigo());

        evento.setEstadoSistema(nuevoEstado);
        eventoRepository.save(evento);

        HistorialEstadoEvento historial = new HistorialEstadoEvento();
        historial.setEvento(evento);
        historial.setEstadoSistema(nuevoEstado);
        historial.setEstadoOrganizador(evento.getEstadoOrganizador());
        historial.setUsuario(administrador);
        historial.setFechaCambio(LocalDateTime.now());
        historialRepository.save(historial);

        auditoriaService.registrar(administrador, AuditoriaService.ACCION_EVENTO_MODERADO_ADMIN,
                "Evento " + idEvento + ": " + estadoActual + " -> " + nuevoEstado.getEstadoSistema()
                + (nuevoEstado.getMotivoCodigo() == null ? "" : " (" + nuevoEstado.getMotivoCodigo() + ")"),
                request);

        log.info("Evento {} moderado por el administrador {} ({} -> {})",
                idEvento, administrador.getIdUsuario(), estadoActual, nuevoEstado.getEstadoSistema());

        return eventoMapper.aTarjeta(evento, null, null);
    }

    /** EN_PROCESO no tiene nada que aprobar todavia; ya aprobado no admite aprobarse de nuevo. */
    private EventoEstadoSistema resolverAprobacion(String estadoActual) {
        if (EstadoEventoSistemaNombre.EN_PROCESO.name().equals(estadoActual)) {
            throw new OperacionNoPermitidaException(
                    "El evento todavia esta en moderacion automatica");
        }
        if (EstadoEventoSistemaNombre.APROBADO_SISTEMA.name().equals(estadoActual)
                || EstadoEventoSistemaNombre.APROBADO_MANUAL.name().equals(estadoActual)) {
            throw new OperacionNoPermitidaException("El evento ya esta aprobado");
        }
        // RECHAZADO_SISTEMA (escenario a) o RECHAZADO_MANUAL (revierte una
        // suspension, escenario b): en ambos casos el destino es el mismo y
        // sin motivo, porque aprobar no necesita justificarse.
        return buscarFila(EstadoEventoSistemaNombre.APROBADO_MANUAL.name(), null);
    }

    /**
     * EN_PROCESO y ya-rechazado no admiten un rechazo nuevo. Si el origen es
     * un evento YA APROBADO, es una suspension disciplinaria y el motivo es
     * obligatorio; si el origen es RECHAZADO_SISTEMA, es la ratificacion de
     * 2da instancia de siempre, sin motivo (igual que antes de este cambio).
     */
    private EventoEstadoSistema resolverRechazo(String estadoActual, String motivoCodigo) {
        if (EstadoEventoSistemaNombre.EN_PROCESO.name().equals(estadoActual)) {
            throw new OperacionNoPermitidaException(
                    "El evento todavia esta en moderacion automatica");
        }
        if (EstadoEventoSistemaNombre.RECHAZADO_MANUAL.name().equals(estadoActual)) {
            throw new OperacionNoPermitidaException("El evento ya esta rechazado o suspendido");
        }

        boolean eraAprobado = EstadoEventoSistemaNombre.APROBADO_SISTEMA.name().equals(estadoActual)
                || EstadoEventoSistemaNombre.APROBADO_MANUAL.name().equals(estadoActual);

        if (!eraAprobado) {
            // RECHAZADO_SISTEMA: ratificacion de 2da instancia, sin motivo.
            return buscarFila(EstadoEventoSistemaNombre.RECHAZADO_MANUAL.name(), null);
        }

        MotivoSuspensionAdmin motivo = validarMotivoSuspension(motivoCodigo);
        return buscarFila(EstadoEventoSistemaNombre.RECHAZADO_MANUAL.name(), motivo.name());
    }

    private MotivoSuspensionAdmin validarMotivoSuspension(String motivoCodigo) {
        if (motivoCodigo == null || motivoCodigo.isBlank()) {
            throw new ReglaNegocioException(
                    "Suspender un evento ya aprobado requiere indicar un motivo");
        }
        try {
            return MotivoSuspensionAdmin.valueOf(motivoCodigo);
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("El motivo '" + motivoCodigo + "' no es valido");
        }
    }

    private EventoEstadoSistema buscarFila(String estado, String motivo) {
        return estadoSistemaRepository.buscarPorEstadoYMotivo(estado, motivo)
                .orElseThrow(() -> new ReglaNegocioException(
                        "El catalogo evento_estado_sistema no tiene la fila " + estado
                        + "/" + motivo));
    }
}
