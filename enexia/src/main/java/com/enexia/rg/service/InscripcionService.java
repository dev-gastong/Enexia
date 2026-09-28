package com.enexia.rg.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.enexia.rg.dto.HistorialEstadoItem;
import com.enexia.rg.dto.InscripcionRequest;
import com.enexia.rg.dto.InscripcionResponse;
import com.enexia.rg.exception.OperacionNoPermitidaException;
import com.enexia.rg.exception.PagoRechazadoException;
import com.enexia.rg.exception.RecursoNoEncontradoException;
import com.enexia.rg.exception.ReglaNegocioException;
import com.enexia.rg.model.CronogramaTicket;
import com.enexia.rg.model.EventoCronograma;
import com.enexia.rg.model.HistorialEstadoInscripcion;
import com.enexia.rg.model.Inscripcion;
import com.enexia.rg.model.InscripcionEstado;
import com.enexia.rg.model.InscripcionEstadoNombre;
import com.enexia.rg.model.PagoEstadoNombre;
import com.enexia.rg.model.Usuario;
import com.enexia.rg.repository.CronogramaTicketRepository;
import com.enexia.rg.repository.HistorialEstadoInscripcionRepository;
import com.enexia.rg.repository.InscripcionEstadoRepository;
import com.enexia.rg.repository.InscripcionRepository;
import com.enexia.rg.repository.PagoEstadoRepository;
import com.enexia.rg.repository.PagoRepository;
import com.enexia.rg.repository.UsuarioRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Modulo 3: participacion del lado del participante (RF-3.1, RF-3.2, RF-3.3, RF-3.6).
 *
 * La reserva en si (RF-3.1/RF-3.2) se delega en {@link RegistroInscripcionService}:
 * ver el javadoc de esa clase para el motivo -- es una separacion obligatoria,
 * no estilistica, para que un pago rechazado pueda persistir su intento sin
 * revertir la inscripcion que lo origino.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InscripcionService {

    private static final String CODIGO_QR_PREFIJO = "ENEXIA-INSC-";

    private final InscripcionRepository inscripcionRepository;
    private final InscripcionEstadoRepository inscripcionEstadoRepository;
    private final CronogramaTicketRepository ticketRepository;
    private final PagoRepository pagoRepository;
    private final PagoEstadoRepository pagoEstadoRepository;
    private final HistorialEstadoInscripcionRepository historialRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;
    private final RegistroInscripcionService registroInscripcionService;

    // =====================================================================
    // RF-3.1 / RF-3.2: alta e inscripcion transaccional
    // =====================================================================

    /**
     * Reserva un ticket para el usuario autenticado.
     *
     * NO ES {@code @Transactional}: delega toda la escritura atomica en
     * {@link RegistroInscripcionService#registrar}, que corre en su propia
     * transaccion (bean distinto, cruza el proxy) y siempre confirma antes de
     * volver. Recien con esa transaccion ya cerrada se decide si el pago
     * rechazado tiene que traducirse en una excepcion: para entonces la fila
     * ya quedo en PENDIENTE_PAGO en la base y no hay nada que una excepcion
     * pueda revertir.
     */
    public InscripcionResponse inscribir(String email, InscripcionRequest datos, HttpServletRequest request) {
        Usuario participante = usuarioRepository.buscarActivoPorEmailConRoles(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro la cuenta"));

        RegistroInscripcionService.ResultadoRegistro resultado = registroInscripcionService.registrar(
                participante, datos.getIdCronogramaTicket(), datos.getMetodoPago(), request);

        // Lectura aparte, DESPUES de que la transaccion de arriba ya confirmo:
        // cada llamada a un repositorio de Spring Data corre en su propia
        // transaccion corta, asi que no hace falta (ni corresponde) envolver
        // esta relectura en un @Transactional propio de este metodo.
        InscripcionResponse respuesta = obtenerPropia(resultado.idInscripcion());

        if (!resultado.aprobado()) {
            throw new PagoRechazadoException(resultado.motivoRechazo());
        }

        log.info("Inscripcion {} resuelta para el usuario {}",
                resultado.idInscripcion(), participante.getIdUsuario());

        return respuesta;
    }

    // =====================================================================
    // RF-3.3: cancelacion voluntaria
    // =====================================================================

    /**
     * Cancela una inscripcion propia (borrado logico, DFD 3.3).
     *
     * ISOLATION READ_COMMITTED: el bloqueo de fila del ticket necesita leer el
     * estado real bajo MariaDB 11.6+ (mismo motivo que {@code EventoService.darDeBaja}).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InscripcionResponse cancelar(String email, Long idInscripcion, HttpServletRequest request) {
        Usuario participante = usuarioRepository.buscarActivoPorEmailConRoles(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro la cuenta"));

        Inscripcion inscripcion = inscripcionRepository.buscarConAsociaciones(idInscripcion)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro la inscripcion"));

        // 404 y no 403 (el DFD 3.3 documenta 403): mismo criterio que
        // EventoService.buscarPropio y MiembrosOrganizacionService -- confirmar
        // que una inscripcion ajena "existe" permite mapear la plataforma
        // iterando ids, y este sistema evita esa fuga en todos los demas
        // recursos propios.
        boolean esPropia = inscripcion.getUsuario() != null
                && inscripcion.getUsuario().getIdUsuario().equals(participante.getIdUsuario());
        if (!esPropia) {
            throw new RecursoNoEncontradoException("No se encontro la inscripcion");
        }

        String estadoActual = nombreEstado(inscripcion);
        if (InscripcionEstadoNombre.CANCELADA.name().equalsIgnoreCase(estadoActual)) {
            throw new OperacionNoPermitidaException("La inscripcion ya estaba cancelada");
        }

        EventoCronograma cronograma = inscripcion.getCronogramaTicket().getCronograma();
        LocalDateTime inicio = LocalDateTime.of(cronograma.getFecha(), cronograma.getHoraInicio());
        if (!inicio.isAfter(LocalDateTime.now())) {
            throw new OperacionNoPermitidaException(
                    "El evento ya comenzo o finalizo: no se puede cancelar la inscripcion");
        }

        boolean estabaConfirmada = InscripcionEstadoNombre.CONFIRMADA.name().equalsIgnoreCase(estadoActual);

        // Bloqueo de fila del ticket: una nueva inscripcion puede estar
        // tocando el mismo cupo en paralelo (mismo patron que
        // RegistroInscripcionService.registrar).
        CronogramaTicket ticket = ticketRepository.bloquearParaActualizar(
                inscripcion.getCronogramaTicket().getIdCronogramaTicket())
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro el ticket asociado"));

        InscripcionEstado cancelada = buscarEstadoInscripcion(InscripcionEstadoNombre.CANCELADA);
        inscripcion.setEstadoInscripcion(cancelada);
        inscripcionRepository.save(inscripcion);
        registrarHistorial(inscripcion, cancelada, participante);

        // Solo se libera cupo si de verdad se habia incrementado al confirmar
        // (RF-3.1/RF-3.2). Una reserva que quedo en PENDIENTE o PENDIENTE_PAGO
        // nunca llego a sumarlo, asi que cancelarla no debe restarlo: se
        // liberaria un lugar que esa reserva nunca ocupo.
        if (estabaConfirmada) {
            int cupoActual = ticket.getCupoActual() == null ? 0 : ticket.getCupoActual();
            ticket.setCupoActual(Math.max(0, cupoActual - 1));
            ticketRepository.save(ticket);
        }

        // RF-3.3: si tenia un pago aprobado, se revierte con un reembolso simulado.
        pagoRepository.findByInscripcionIdInscripcion(idInscripcion).ifPresent(pago -> {
            String estadoPago = pago.getEstadoPago() == null ? null : pago.getEstadoPago().getNombreEstado();
            if (PagoEstadoNombre.COMPLETADO.name().equalsIgnoreCase(estadoPago)) {
                pago.setEstadoPago(pagoEstadoRepository
                        .findByNombreEstadoIgnoreCase(PagoEstadoNombre.REEMBOLSADO.name())
                        .orElseThrow(() -> new ReglaNegocioException(
                                "El catalogo pago_estado no esta inicializado")));
                pagoRepository.save(pago);
            }
        });

        auditoriaService.registrar(participante, AuditoriaService.ACCION_INSCRIPCION_CANCELADA,
                "Inscripcion " + idInscripcion + " cancelada", request);

        log.info("Inscripcion {} cancelada por el usuario {}", idInscripcion, participante.getIdUsuario());

        return mapear(inscripcion, historialRepository.buscarPorInscripcion(idInscripcion).stream()
                .map(this::mapearHistorialItem).toList());
    }

    // =====================================================================
    // RF-3.6: historial centralizado
    // =====================================================================

    @Transactional(readOnly = true)
    public Page<InscripcionResponse> historial(String email, Pageable paginado) {
        Usuario participante = usuarioRepository.buscarActivoPorEmailConRoles(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro la cuenta"));

        Page<Inscripcion> pagina = inscripcionRepository.listarDeUsuario(
                participante.getIdUsuario(), paginado);

        List<Long> ids = pagina.getContent().stream().map(Inscripcion::getIdInscripcion).toList();

        Map<Long, List<HistorialEstadoItem>> historialPorInscripcion = ids.isEmpty()
                ? Map.of()
                : historialRepository.buscarPorInscripciones(ids).stream()
                        .collect(Collectors.groupingBy(
                                h -> h.getInscripcion().getIdInscripcion(),
                                Collectors.mapping(this::mapearHistorialItem, Collectors.toList())));

        return pagina.map(inscripcion -> mapear(
                inscripcion, historialPorInscripcion.getOrDefault(inscripcion.getIdInscripcion(), List.of())));
    }

    // =====================================================================
    // Auxiliares
    // =====================================================================

    private InscripcionResponse obtenerPropia(Long idInscripcion) {
        Inscripcion inscripcion = inscripcionRepository.buscarConAsociaciones(idInscripcion)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro la inscripcion"));

        return mapear(inscripcion, historialRepository.buscarPorInscripcion(idInscripcion).stream()
                .map(this::mapearHistorialItem).toList());
    }

    private void registrarHistorial(Inscripcion inscripcion, InscripcionEstado estado, Usuario usuario) {
        HistorialEstadoInscripcion historial = new HistorialEstadoInscripcion();
        historial.setInscripcion(inscripcion);
        historial.setEstadoInscripcion(estado);
        historial.setUsuarioCambio(usuario);
        historial.setFechaCambio(LocalDateTime.now());
        historialRepository.save(historial);
    }

    private InscripcionEstado buscarEstadoInscripcion(InscripcionEstadoNombre nombre) {
        return inscripcionEstadoRepository.findByNombreEstadoIgnoreCase(nombre.name())
                .orElseThrow(() -> new ReglaNegocioException(
                        "El catalogo inscripcion_estado no esta inicializado"));
    }

    private String nombreEstado(Inscripcion inscripcion) {
        return inscripcion.getEstadoInscripcion() == null
                ? null : inscripcion.getEstadoInscripcion().getNombreEstado();
    }

    private InscripcionResponse mapear(Inscripcion inscripcion, List<HistorialEstadoItem> historial) {
        CronogramaTicket ticket = inscripcion.getCronogramaTicket();
        EventoCronograma cronograma = ticket == null ? null : ticket.getCronograma();
        String estado = nombreEstado(inscripcion);

        return InscripcionResponse.builder()
                .idInscripcion(inscripcion.getIdInscripcion())
                .idEvento(cronograma == null || cronograma.getEvento() == null
                        ? null : cronograma.getEvento().getIdEvento())
                .eventoNombre(cronograma == null || cronograma.getEvento() == null
                        ? null : cronograma.getEvento().getNombre())
                .tipoTicket(ticket == null || ticket.getTipoTicket() == null
                        ? null : ticket.getTipoTicket().getNombre())
                .fechaCronograma(cronograma == null ? null : cronograma.getFecha())
                .horaInicio(cronograma == null ? null : cronograma.getHoraInicio())
                .estado(estado)
                .fechaInscripcion(inscripcion.getFechaInscripcion())
                .precioAbonado(inscripcion.getPrecioAbonado())
                .codigoQr(InscripcionEstadoNombre.CONFIRMADA.name().equalsIgnoreCase(estado)
                        ? CODIGO_QR_PREFIJO + inscripcion.getIdInscripcion() : null)
                .historial(historial)
                .build();
    }

    private HistorialEstadoItem mapearHistorialItem(HistorialEstadoInscripcion historial) {
        return HistorialEstadoItem.builder()
                .estado(historial.getEstadoInscripcion() == null
                        ? null : historial.getEstadoInscripcion().getNombreEstado())
                .fechaCambio(historial.getFechaCambio())
                .build();
    }
}
