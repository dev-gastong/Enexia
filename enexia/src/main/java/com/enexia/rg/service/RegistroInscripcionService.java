package com.enexia.rg.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import com.enexia.rg.exception.OperacionNoPermitidaException;
import com.enexia.rg.exception.RecursoDuplicadoException;
import com.enexia.rg.exception.RecursoNoEncontradoException;
import com.enexia.rg.exception.ReglaNegocioException;
import com.enexia.rg.model.CronogramaTicket;
import com.enexia.rg.model.EventoCronograma;
import com.enexia.rg.model.HistorialEstadoInscripcion;
import com.enexia.rg.model.Inscripcion;
import com.enexia.rg.model.InscripcionEstado;
import com.enexia.rg.model.InscripcionEstadoNombre;
import com.enexia.rg.model.Pago;
import com.enexia.rg.model.PagoEstado;
import com.enexia.rg.model.PagoEstadoNombre;
import com.enexia.rg.model.Usuario;
import com.enexia.rg.repository.CronogramaTicketRepository;
import com.enexia.rg.repository.HistorialEstadoInscripcionRepository;
import com.enexia.rg.repository.InscripcionEstadoRepository;
import com.enexia.rg.repository.InscripcionRepository;
import com.enexia.rg.repository.PagoEstadoRepository;
import com.enexia.rg.repository.PagoRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fase atomica de "reservar un ticket" (RF-3.1, RF-3.2; DFD 3.1.1 a 3.2.3).
 *
 * POR QUE ES UN BEAN APARTE Y NO UN METODO MAS DE {@link InscripcionService}
 * Cuando un ticket de pago es rechazado por la pasarela simulada, el DFD pide
 * dos cosas a la vez: que la inscripcion y el pago FALLIDO queden PERSISTIDOS
 * (para que el participante pueda ver el intento y reintentar el pago) y que
 * la peticion HTTP responda 402. Esas dos cosas chocan si se resuelven en la
 * misma transaccion: lanzar una excepcion desde un metodo {@code @Transactional}
 * revierte TODO lo que esa transaccion escribio, inscripcion incluida.
 *
 * La solucion NO puede ser {@code @Transactional(REQUIRES_NEW)} al estilo
 * {@code AuditoriaService.registrarAparte}: esa variante abre una transaccion
 * nueva que necesita leer la fila de {@code Inscripcion} recien creada para
 * poder insertar el {@code Pago} que la referencia por clave foranea, y esa
 * fila la tiene bloqueada (sin confirmar) la transaccion original todavia
 * abierta. Las dos transacciones se interbloquean (mismo caso que documenta
 * el javadoc de {@code AuditoriaService}: usar REQUIRES_NEW sobre una fila que
 * la transaccion en curso todavia no confirmo).
 *
 * Por eso esta clase resuelve la reserva COMPLETA (crear PENDIENTE, intentar
 * el pago si corresponde, terminar en CONFIRMADA o en PENDIENTE_PAGO) en UNA
 * sola transaccion que siempre COMMITEA, nunca lanza por un pago rechazado
 * -- es una salida de negocio valida, no un error -- y devuelve el veredicto
 * en su valor de retorno. {@link InscripcionService#inscribir} llama a este
 * metodo desde OTRO bean (cruza el proxy, la transaccion se confirma al
 * volver) y recien ahi, con la fila ya confirmada en la base, decide si
 * lanzar {@link com.enexia.rg.exception.PagoRechazadoException} para que el
 * cliente reciba el 402. Para entonces no hay nada que revertir.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegistroInscripcionService {

    /** Veredicto de un intento de reserva, ya confirmado en la base. */
    public record ResultadoRegistro(Long idInscripcion, boolean aprobado, String motivoRechazo) {

        public static ResultadoRegistro aprobado(Long idInscripcion) {
            return new ResultadoRegistro(idInscripcion, true, null);
        }

        public static ResultadoRegistro rechazado(Long idInscripcion, String motivo) {
            return new ResultadoRegistro(idInscripcion, false, motivo);
        }
    }

    private final InscripcionRepository inscripcionRepository;
    private final InscripcionEstadoRepository inscripcionEstadoRepository;
    private final CronogramaTicketRepository ticketRepository;
    private final PagoRepository pagoRepository;
    private final PagoEstadoRepository pagoEstadoRepository;
    private final HistorialEstadoInscripcionRepository historialRepository;
    private final AuditoriaService auditoriaService;
    private final PasarelaPagoSimuladaService pasarela;

    /**
     * Reserva un ticket para el participante indicado.
     *
     * ISOLATION READ_COMMITTED: mismo motivo que {@code EventoService.darDeBaja}
     * -- el SELECT ... FOR UPDATE del bloqueo de fila necesita leer el estado
     * real y no una instantanea vieja bajo MariaDB 11.6+.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResultadoRegistro registrar(Usuario participante, Long idCronogramaTicket,
                                       String metodoPago, HttpServletRequest request) {

        // --- Paso 3.1.1: bloqueo de fila + validacion sincronica de cupo.
        CronogramaTicket ticket = ticketRepository.bloquearParaActualizar(idCronogramaTicket)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro el ticket indicado"));

        // Vigencia (RF-3.1, DFD 3.1.1): el DFD original no lo contemplaba, pero
        // sin este chequeo se podia reservar un cupo en una funcion que ya
        // arranco o ya termino. Se compara contra hora_inicio, no hora_fin:
        // dejar sumarse a un evento en curso permitia inscribirse y, para
        // cuando la funcion terminaba, calificar con una inscripcion que
        // nunca correspondio a haber asistido desde el principio. Mismo
        // umbral que usa InscripcionService.cancelar para el corte de
        // cancelacion.
        EventoCronograma cronogramaDelTicket = ticket.getCronograma();
        LocalDateTime inicioDeFuncion = LocalDateTime.of(
                cronogramaDelTicket.getFecha(), cronogramaDelTicket.getHoraInicio());
        if (!inicioDeFuncion.isAfter(LocalDateTime.now())) {
            throw new OperacionNoPermitidaException("Las inscripciones para este evento ya cerraron");
        }

        // Unicidad (ver javadoc de InscripcionRepository.existeActivaDeUsuarioEnCronograma):
        // sin este chequeo, repetir la peticion crea una inscripcion nueva por
        // cada click y consume un cupo por cada una.
        Long idCronograma = ticket.getCronograma().getIdCronograma();
        if (inscripcionRepository.existeActivaDeUsuarioEnCronograma(participante.getIdUsuario(), idCronograma)) {
            throw new RecursoDuplicadoException(
                    "Ya tenes una inscripcion activa para este cronograma. Cancelala antes de volver a inscribirte.");
        }

        int cupoMaximo = ticket.getCupoMaximo() == null ? 0 : ticket.getCupoMaximo();
        int cupoActual = ticket.getCupoActual() == null ? 0 : ticket.getCupoActual();
        if (cupoActual >= cupoMaximo) {
            throw new OperacionNoPermitidaException("No quedan cupos disponibles para ese ticket");
        }

        // --- Paso 3.1.2: crear la inscripcion PENDIENTE.
        InscripcionEstado pendiente = buscarEstadoInscripcion(InscripcionEstadoNombre.PENDIENTE);

        Inscripcion inscripcion = new Inscripcion();
        inscripcion.setCronogramaTicket(ticket);
        inscripcion.setUsuario(participante);
        inscripcion.setEstadoInscripcion(pendiente);
        inscripcion.setFechaInscripcion(LocalDate.now());
        inscripcion.setPrecioAbonado(ticket.getPrecio());
        inscripcion = inscripcionRepository.save(inscripcion);

        registrarHistorial(inscripcion, pendiente, participante);

        auditoriaService.registrar(participante, AuditoriaService.ACCION_INSCRIPCION_CREADA,
                "Inscripcion " + inscripcion.getIdInscripcion() + " creada en PENDIENTE para el ticket "
                + ticket.getIdCronogramaTicket(), request);

        BigDecimal precio = ticket.getPrecio() == null ? BigDecimal.ZERO : ticket.getPrecio();

        // --- Bifurcacion RF-3.1 (gratuito) / RF-3.2 (pago).
        if (precio.compareTo(BigDecimal.ZERO) == 0) {
            confirmar(inscripcion, ticket, participante, request);
            return ResultadoRegistro.aprobado(inscripcion.getIdInscripcion());
        }

        return procesarPago(inscripcion, ticket, participante, precio, metodoPago, request);
    }

    /** Camino B.2 del DFD 3.2: pago aprobado -> Pago COMPLETADO + confirmar inscripcion. */
    private ResultadoRegistro procesarPago(Inscripcion inscripcion, CronogramaTicket ticket,
                                           Usuario participante, BigDecimal monto, String metodoPago,
                                           HttpServletRequest request) {

        PasarelaPagoSimuladaService.ResultadoPago resultado = pasarela.procesar(monto, metodoPago);

        if (!resultado.aprobado()) {
            // --- Camino B.1: pago rechazado. Se persiste igual (ver javadoc de
            // la clase): la inscripcion NO se pierde, queda en PENDIENTE_PAGO.
            Pago pago = nuevoPago(inscripcion, monto, metodoPago, resultado.tokenOperacion(),
                    buscarEstadoPago(PagoEstadoNombre.FALLIDO));
            pagoRepository.save(pago);

            InscripcionEstado pendientePago = buscarEstadoInscripcion(InscripcionEstadoNombre.PENDIENTE_PAGO);
            inscripcion.setEstadoInscripcion(pendientePago);
            inscripcionRepository.save(inscripcion);
            registrarHistorial(inscripcion, pendientePago, participante);

            auditoriaService.registrar(participante, AuditoriaService.ACCION_PAGO_RECHAZADO,
                    "Pago rechazado para la inscripcion " + inscripcion.getIdInscripcion(), request);

            log.info("Pago rechazado para la inscripcion {} del usuario {}",
                    inscripcion.getIdInscripcion(), participante.getIdUsuario());

            return ResultadoRegistro.rechazado(inscripcion.getIdInscripcion(), resultado.motivoRechazo());
        }

        Pago pago = nuevoPago(inscripcion, monto, metodoPago, resultado.tokenOperacion(),
                buscarEstadoPago(PagoEstadoNombre.COMPLETADO));
        pagoRepository.save(pago);

        confirmar(inscripcion, ticket, participante, request);
        return ResultadoRegistro.aprobado(inscripcion.getIdInscripcion());
    }

    /** Caminos A y B.2 del DFD 3.1/3.2: mutar a CONFIRMADA e incrementar el cupo. */
    private void confirmar(Inscripcion inscripcion, CronogramaTicket ticket,
                           Usuario participante, HttpServletRequest request) {

        InscripcionEstado confirmada = buscarEstadoInscripcion(InscripcionEstadoNombre.CONFIRMADA);
        inscripcion.setEstadoInscripcion(confirmada);
        inscripcionRepository.save(inscripcion);

        int cupoActual = ticket.getCupoActual() == null ? 0 : ticket.getCupoActual();
        ticket.setCupoActual(cupoActual + 1);
        ticketRepository.save(ticket);

        registrarHistorial(inscripcion, confirmada, participante);

        auditoriaService.registrar(participante, AuditoriaService.ACCION_INSCRIPCION_CONFIRMADA,
                "Inscripcion " + inscripcion.getIdInscripcion() + " confirmada", request);
    }

    private Pago nuevoPago(Inscripcion inscripcion, BigDecimal monto, String metodoPago,
                           String tokenOperacion, PagoEstado estado) {
        Pago pago = new Pago();
        pago.setInscripcion(inscripcion);
        pago.setEstadoPago(estado);
        pago.setMonto(monto);
        pago.setMetodoPago(metodoPago);
        pago.setTokenOperacion(tokenOperacion);
        pago.setFechaPago(LocalDateTime.now());
        return pago;
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

    private PagoEstado buscarEstadoPago(PagoEstadoNombre nombre) {
        return pagoEstadoRepository.findByNombreEstadoIgnoreCase(nombre.name())
                .orElseThrow(() -> new ReglaNegocioException(
                        "El catalogo pago_estado no esta inicializado"));
    }
}
