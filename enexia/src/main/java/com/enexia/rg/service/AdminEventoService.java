package com.enexia.rg.service;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.enexia.rg.dto.AdminDecisionEventoRequest;
import com.enexia.rg.dto.EventoAdminDetalleResponse;
import com.enexia.rg.dto.EventoResponse;
import com.enexia.rg.exception.OperacionNoPermitidaException;
import com.enexia.rg.exception.RecursoNoEncontradoException;
import com.enexia.rg.exception.ReglaNegocioException;
import com.enexia.rg.model.EstadoEventoSistemaNombre;
import com.enexia.rg.model.Ciudad;
import com.enexia.rg.model.Evento;
import com.enexia.rg.model.EventoDetalle;
import com.enexia.rg.model.EventoEstadoSistema;
import com.enexia.rg.model.HistorialEstadoEvento;
import com.enexia.rg.model.MotivoSuspensionAdmin;
import com.enexia.rg.model.Usuario;
import com.enexia.rg.repository.CronogramaTicketRepository;
import com.enexia.rg.repository.EventoDetalleRepository;
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
 *       APROBADO_MANUAL. Ratificar el rechazo NO es una decision: el evento
 *       ya esta fuera del catalogo y marcarlo RECHAZADO_MANUAL no cambiaria
 *       nada para nadie (decidido 2026-10-11), asi que un RECHAZAR sobre
 *       RECHAZADO_SISTEMA responde 409 igual que sobre RECHAZADO_MANUAL.
 *   (b) Suspension disciplinaria: el evento esta APROBADO_SISTEMA o
 *       APROBADO_MANUAL (ya esta publicado). El administrador lo puede pasar
 *       a RECHAZADO_MANUAL con un motivo de {@link MotivoSuspensionAdmin} y
 *       una observacion de texto libre (ambos obligatorios), y mas adelante
 *       revertir esa suspension a APROBADO_MANUAL si corresponde.
 *
 * EN_PROCESO y EN_REVISION quedan deliberadamente fuera: el pipeline
 * asincrono todavia no dictamino (RF-2.2, RF-2.7) y no hay una version firme
 * del contenido que aprobar o rechazar.
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

    /** Largo minimo de la observacion de una suspension: evita el "ok" o el "." que no deja constancia de nada. */
    static final int OBSERVACION_MINIMA = 10;
    static final int OBSERVACION_MAXIMA = 500;

    private final EventoRepository eventoRepository;
    private final EventoEstadoSistemaRepository estadoSistemaRepository;
    private final HistorialEstadoEventoRepository historialRepository;
    private final UsuarioRepository usuarioRepository;
    private final EventoDetalleRepository detalleRepository;
    private final CronogramaTicketRepository ticketRepository;
    private final EventoService eventoService;
    private final EventoMapper eventoMapper;
    private final AuditoriaService auditoriaService;
    private final EmailService emailService;

    @Transactional(readOnly = true)
    public Page<EventoResponse> listarParaModeracion(String texto, Pageable paginado) {
        Page<Evento> pagina = eventoRepository.buscarParaModeracion(
                eventoService.normalizarFiltro(texto), paginado);
        return eventoService.mapearPagina(pagina);
    }

    /**
     * Detalle de un evento para decidir sobre el (RF-6.1).
     *
     * A diferencia de la ficha publica, no registra visita y funciona sea cual
     * sea el estado: justamente los eventos que el administrador mas necesita
     * abrir son los que el catalogo oculta.
     */
    @Transactional(readOnly = true)
    public EventoAdminDetalleResponse obtenerDetalle(Long idEvento) {
        Evento evento = eventoRepository.findById(idEvento)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro el evento"));

        EventoDetalle detalle = detalleRepository.buscarConUbicacion(idEvento).orElse(null);
        Ciudad ciudad = detalle == null || detalle.getUbicacion() == null
                ? null : detalle.getUbicacion().getCiudad();

        HistorialEstadoEvento ultimo = historialRepository
                .findFirstByEventoIdEventoOrderByFechaCambioDescIdHistorialDesc(idEvento)
                .orElse(null);

        return EventoAdminDetalleResponse.builder()
                .idEvento(evento.getIdEvento())
                .nombre(evento.getNombre() != null ? evento.getNombre() : "(Evento en validacion)")
                .urlPortada(evento.getUrlPortada())
                .descripcion(detalle == null ? null : detalle.getDescripcion())
                .categoria(evento.getCategoria() == null ? null : evento.getCategoria().getNombreCategoria())
                .organizador(eventoMapper.firmaOrganizador(evento))
                .organizadorEsOrganizacion(evento.getPersonaJuridica() != null)
                .organizadorNickname(evento.getOrganizador() == null
                        ? null : evento.getOrganizador().getNickname())
                .estadoSistema(evento.getEstadoSistema() == null ? null
                        : evento.getEstadoSistema().getEstadoSistema())
                .motivoCodigo(evento.getEstadoSistema() == null ? null
                        : evento.getEstadoSistema().getMotivoCodigo())
                .estadoOrganizador(evento.getEstadoOrganizador() == null ? null
                        : evento.getEstadoOrganizador().getEstadoOrganizador())
                .observacionAdmin(ultimo == null ? null : ultimo.getObservacion())
                .fechaUltimaDecision(ultimo == null ? null : ultimo.getFechaCambio())
                .cupoMaximo(ticketRepository.sumarCupoMaximo(idEvento))
                .ciudad(ciudad == null ? null : ciudad.getNombre())
                .provincia(ciudad == null || ciudad.getProvincia() == null
                        ? null : ciudad.getProvincia().getNombre())
                .fechaCreacion(evento.getFechaCreacion())
                .build();
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
                : resolverRechazo(estadoActual, datos.getMotivoCodigo(), datos.getObservacion());

        // Solo una suspension lleva observacion (ya validada arriba); en
        // aprobar se descarta aunque el cliente la mande, para que el historial
        // no cargue texto que nadie pidio ni reviso.
        String observacion = esAprobar ? null : datos.getObservacion().trim();

        evento.setEstadoSistema(nuevoEstado);
        eventoRepository.save(evento);

        HistorialEstadoEvento historial = new HistorialEstadoEvento();
        historial.setEvento(evento);
        historial.setEstadoSistema(nuevoEstado);
        historial.setEstadoOrganizador(evento.getEstadoOrganizador());
        historial.setUsuario(administrador);
        historial.setFechaCambio(LocalDateTime.now());
        historial.setObservacion(observacion);
        historialRepository.save(historial);

        auditoriaService.registrar(administrador, AuditoriaService.ACCION_EVENTO_MODERADO_ADMIN,
                "Evento " + idEvento + ": " + estadoActual + " -> " + nuevoEstado.getEstadoSistema()
                + (nuevoEstado.getMotivoCodigo() == null ? "" : " (" + nuevoEstado.getMotivoCodigo() + ")"),
                request);

        log.info("Evento {} moderado por el administrador {} ({} -> {})",
                idEvento, administrador.getIdUsuario(), estadoActual, nuevoEstado.getEstadoSistema());

        avisarAlOrganizador(evento, estadoActual, esAprobar, nuevoEstado, observacion);

        return eventoMapper.aTarjeta(evento, null, null);
    }

    /**
     * Cuenta al organizador lo que decidio el administrador sobre su evento.
     *
     *   - Suspender: motivo y observacion, para que sepa que corregir o apelar.
     *   - Aprobar sobre una SUSPENSION: "volvio a estar publicado".
     *   - Aprobar sobre un rechazo del sistema: es la misma noticia que el
     *     resultado de la moderacion automatica ("ya esta publicado"), no una
     *     reactivacion, porque nunca estuvo al aire.
     */
    private void avisarAlOrganizador(Evento evento, String estadoAnterior, boolean esAprobar,
                                     EventoEstadoSistema nuevoEstado, String observacion) {
        if (evento.getOrganizador() == null || evento.getOrganizador().getEmail() == null) {
            return;
        }
        String email = evento.getOrganizador().getEmail();

        if (!esAprobar) {
            emailService.enviarEventoSuspendido(
                    email, evento.getNombre(), nuevoEstado.getMotivoCodigo(), observacion);
        } else if (EstadoEventoSistemaNombre.RECHAZADO_MANUAL.name().equals(estadoAnterior)) {
            emailService.enviarEventoReactivado(email, evento.getNombre());
        } else {
            emailService.enviarResultadoModeracionEvento(email, evento.getNombre(), true, null);
        }
    }

    /** En moderacion no hay nada que aprobar todavia; ya aprobado no admite aprobarse de nuevo. */
    private EventoEstadoSistema resolverAprobacion(String estadoActual) {
        if (enModeracionAutomatica(estadoActual)) {
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
     * Rechazar solo tiene sentido sobre un evento que hoy esta publicado: es
     * la suspension disciplinaria (RF-6.1b) y exige motivo y observacion.
     * Sobre uno en moderacion no hay version firme que rechazar, y sobre uno
     * ya rechazado (por el sistema o por un admin) no cambiaria nada.
     */
    private EventoEstadoSistema resolverRechazo(String estadoActual, String motivoCodigo,
                                                String observacion) {
        if (enModeracionAutomatica(estadoActual)) {
            throw new OperacionNoPermitidaException(
                    "El evento todavia esta en moderacion automatica");
        }
        if (EstadoEventoSistemaNombre.RECHAZADO_SISTEMA.name().equals(estadoActual)
                || EstadoEventoSistemaNombre.RECHAZADO_MANUAL.name().equals(estadoActual)) {
            throw new OperacionNoPermitidaException("El evento ya esta rechazado o suspendido");
        }

        MotivoSuspensionAdmin motivo = validarMotivoSuspension(motivoCodigo);
        validarObservacion(observacion);
        return buscarFila(EstadoEventoSistemaNombre.RECHAZADO_MANUAL.name(), motivo.name());
    }

    private boolean enModeracionAutomatica(String estadoActual) {
        return EstadoEventoSistemaNombre.EN_PROCESO.name().equals(estadoActual)
                || EstadoEventoSistemaNombre.EN_REVISION.name().equals(estadoActual);
    }

    private void validarObservacion(String observacion) {
        String limpia = observacion == null ? "" : observacion.trim();
        if (limpia.length() < OBSERVACION_MINIMA) {
            throw new ReglaNegocioException("Suspender un evento requiere una observacion de al menos "
                    + OBSERVACION_MINIMA + " caracteres");
        }
        if (limpia.length() > OBSERVACION_MAXIMA) {
            throw new ReglaNegocioException("La observacion no puede superar los "
                    + OBSERVACION_MAXIMA + " caracteres");
        }
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
