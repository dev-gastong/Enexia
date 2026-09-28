package com.enexia.rg.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.enexia.rg.dto.InscripcionRequest;
import com.enexia.rg.dto.InscripcionResponse;
import com.enexia.rg.exception.OperacionNoPermitidaException;
import com.enexia.rg.exception.PagoRechazadoException;
import com.enexia.rg.exception.RecursoNoEncontradoException;
import com.enexia.rg.model.CronogramaTicket;
import com.enexia.rg.model.Evento;
import com.enexia.rg.model.EventoCronograma;
import com.enexia.rg.model.Inscripcion;
import com.enexia.rg.model.InscripcionEstado;
import com.enexia.rg.model.InscripcionEstadoNombre;
import com.enexia.rg.model.Pago;
import com.enexia.rg.model.PagoEstado;
import com.enexia.rg.model.PagoEstadoNombre;
import com.enexia.rg.model.TipoTicket;
import com.enexia.rg.model.Usuario;
import com.enexia.rg.repository.CronogramaTicketRepository;
import com.enexia.rg.repository.HistorialEstadoInscripcionRepository;
import com.enexia.rg.repository.InscripcionEstadoRepository;
import com.enexia.rg.repository.InscripcionRepository;
import com.enexia.rg.repository.PagoEstadoRepository;
import com.enexia.rg.repository.PagoRepository;
import com.enexia.rg.repository.UsuarioRepository;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Pruebas de {@link InscripcionService}: orquestacion de la reserva (RF-3.1/3.2),
 * cancelacion (RF-3.3) e historial (RF-3.6).
 *
 * La fase atomica de reserva en si (aprobar/rechazar el pago) se prueba en
 * {@link RegistroInscripcionServiceTest}; aca solo se verifica que este
 * service delega correctamente y decide el 402 despues de leer el resultado.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InscripcionService - reserva, cancelacion e historial")
class InscripcionServiceTest {

    @Mock private InscripcionRepository inscripcionRepository;
    @Mock private InscripcionEstadoRepository inscripcionEstadoRepository;
    @Mock private CronogramaTicketRepository ticketRepository;
    @Mock private PagoRepository pagoRepository;
    @Mock private PagoEstadoRepository pagoEstadoRepository;
    @Mock private HistorialEstadoInscripcionRepository historialRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AuditoriaService auditoriaService;
    @Mock private RegistroInscripcionService registroInscripcionService;
    @Mock private HttpServletRequest request;

    @InjectMocks private InscripcionService servicio;

    private static final String EMAIL = "participante@enexia.test";
    private static final Long ID_USUARIO = 1L;
    private static final Long ID_INSCRIPCION = 100L;

    private Usuario participante;

    @BeforeEach
    void prepararEscenario() {
        participante = new Usuario();
        participante.setIdUsuario(ID_USUARIO);
        participante.setEmail(EMAIL);

        when(usuarioRepository.buscarActivoPorEmailConRoles(EMAIL)).thenReturn(Optional.of(participante));
        when(historialRepository.buscarPorInscripcion(anyLong())).thenReturn(List.of());
    }

    // =====================================================================
    // Inscribir (orquestacion sobre RegistroInscripcionService)
    // =====================================================================

    @Nested
    @DisplayName("Inscribir")
    class Inscribir {

        @Test
        @DisplayName("Aprobado: devuelve la inscripcion sin lanzar")
        void aprobadoDevuelveRespuesta() {
            when(registroInscripcionService.registrar(participante, 5L, "tarjeta", request))
                    .thenReturn(RegistroInscripcionService.ResultadoRegistro.aprobado(ID_INSCRIPCION));
            when(inscripcionRepository.buscarConAsociaciones(ID_INSCRIPCION))
                    .thenReturn(Optional.of(inscripcionConfirmada()));

            InscripcionRequest datos = new InscripcionRequest();
            datos.setIdCronogramaTicket(5L);
            datos.setMetodoPago("tarjeta");

            InscripcionResponse respuesta = servicio.inscribir(EMAIL, datos, request);

            assertThat(respuesta.getIdInscripcion()).isEqualTo(ID_INSCRIPCION);
            assertThat(respuesta.getEstado()).isEqualTo("CONFIRMADA");
            assertThat(respuesta.getCodigoQr()).isEqualTo("ENEXIA-INSC-" + ID_INSCRIPCION);
        }

        @Test
        @DisplayName("Rechazado: la fila ya esta persistida, pero igual lanza PagoRechazadoException (402)")
        void rechazadoLanzaExcepcionDespuesDePersistir() {
            when(registroInscripcionService.registrar(participante, 5L, "rechazar", request))
                    .thenReturn(RegistroInscripcionService.ResultadoRegistro.rechazado(
                            ID_INSCRIPCION, "La pasarela de pago rechazo la operacion"));
            when(inscripcionRepository.buscarConAsociaciones(ID_INSCRIPCION))
                    .thenReturn(Optional.of(inscripcionPendientePago()));

            InscripcionRequest datos = new InscripcionRequest();
            datos.setIdCronogramaTicket(5L);
            datos.setMetodoPago("rechazar");

            assertThatThrownBy(() -> servicio.inscribir(EMAIL, datos, request))
                    .isInstanceOf(PagoRechazadoException.class);

            // La lectura posterior a la persistencia SI se hizo: confirma que el
            // 402 se decide DESPUES de que la transaccion de RegistroInscripcionService
            // ya confirmo, no en su lugar.
            verify(inscripcionRepository).buscarConAsociaciones(ID_INSCRIPCION);
        }
    }

    // =====================================================================
    // Cancelar
    // =====================================================================

    @Nested
    @DisplayName("Cancelar")
    class Cancelar {

        @Test
        @DisplayName("Inscripcion ajena: 404 y no 403")
        void inscripcionAjenaEsNoEncontrada() {
            Usuario otro = new Usuario();
            otro.setIdUsuario(999L);
            Inscripcion inscripcion = inscripcionConfirmada();
            inscripcion.setUsuario(otro);
            when(inscripcionRepository.buscarConAsociaciones(ID_INSCRIPCION)).thenReturn(Optional.of(inscripcion));

            assertThatThrownBy(() -> servicio.cancelar(EMAIL, ID_INSCRIPCION, request))
                    .isInstanceOf(RecursoNoEncontradoException.class);

            verify(inscripcionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Ya cancelada: no se puede cancelar dos veces")
        void yaCanceladaNoSePuedeRecancelar() {
            Inscripcion inscripcion = inscripcionConfirmada();
            inscripcion.setEstadoInscripcion(estado(InscripcionEstadoNombre.CANCELADA));
            when(inscripcionRepository.buscarConAsociaciones(ID_INSCRIPCION)).thenReturn(Optional.of(inscripcion));

            assertThatThrownBy(() -> servicio.cancelar(EMAIL, ID_INSCRIPCION, request))
                    .isInstanceOf(OperacionNoPermitidaException.class);
        }

        @Test
        @DisplayName("El evento ya comenzo: no se puede cancelar")
        void eventoYaComenzadoNoSePuedeCancelar() {
            Inscripcion inscripcion = inscripcionConfirmada();
            inscripcion.getCronogramaTicket().getCronograma().setFecha(LocalDate.now().minusDays(1));
            when(inscripcionRepository.buscarConAsociaciones(ID_INSCRIPCION)).thenReturn(Optional.of(inscripcion));

            assertThatThrownBy(() -> servicio.cancelar(EMAIL, ID_INSCRIPCION, request))
                    .isInstanceOf(OperacionNoPermitidaException.class);
        }

        @Test
        @DisplayName("Confirmada: libera un cupo y reembolsa el pago completado")
        void confirmadaLiberaCupoYReembolsaPago() {
            Inscripcion inscripcion = inscripcionConfirmada();
            when(inscripcionRepository.buscarConAsociaciones(ID_INSCRIPCION)).thenReturn(Optional.of(inscripcion));

            CronogramaTicket ticket = inscripcion.getCronogramaTicket();
            ticket.setCupoActual(4);
            when(ticketRepository.bloquearParaActualizar(ticket.getIdCronogramaTicket()))
                    .thenReturn(Optional.of(ticket));
            when(inscripcionEstadoRepository.findByNombreEstadoIgnoreCase(InscripcionEstadoNombre.CANCELADA.name()))
                    .thenReturn(Optional.of(estado(InscripcionEstadoNombre.CANCELADA)));

            Pago pago = new Pago();
            pago.setEstadoPago(estadoPago(PagoEstadoNombre.COMPLETADO));
            when(pagoRepository.findByInscripcionIdInscripcion(ID_INSCRIPCION)).thenReturn(Optional.of(pago));
            when(pagoEstadoRepository.findByNombreEstadoIgnoreCase(PagoEstadoNombre.REEMBOLSADO.name()))
                    .thenReturn(Optional.of(estadoPago(PagoEstadoNombre.REEMBOLSADO)));

            InscripcionResponse respuesta = servicio.cancelar(EMAIL, ID_INSCRIPCION, request);

            assertThat(respuesta.getEstado()).isEqualTo("CANCELADA");
            assertThat(ticket.getCupoActual()).isEqualTo(3);
            assertThat(pago.getEstadoPago().getNombreEstado()).isEqualTo("REEMBOLSADO");
        }

        @Test
        @DisplayName("Pendiente de pago: cancela pero NO toca el cupo (nunca lo habia sumado)")
        void pendienteNoTocaCupo() {
            Inscripcion inscripcion = inscripcionPendientePago();
            when(inscripcionRepository.buscarConAsociaciones(ID_INSCRIPCION)).thenReturn(Optional.of(inscripcion));

            CronogramaTicket ticket = inscripcion.getCronogramaTicket();
            ticket.setCupoActual(2);
            when(ticketRepository.bloquearParaActualizar(ticket.getIdCronogramaTicket()))
                    .thenReturn(Optional.of(ticket));
            when(inscripcionEstadoRepository.findByNombreEstadoIgnoreCase(InscripcionEstadoNombre.CANCELADA.name()))
                    .thenReturn(Optional.of(estado(InscripcionEstadoNombre.CANCELADA)));

            servicio.cancelar(EMAIL, ID_INSCRIPCION, request);

            assertThat(ticket.getCupoActual()).isEqualTo(2);
            verify(ticketRepository, never()).save(any());
        }
    }

    // =====================================================================
    // Historial
    // =====================================================================

    @Nested
    @DisplayName("Historial")
    class Historial {

        @Test
        @DisplayName("Devuelve la pagina mapeada con su historial agrupado por inscripcion")
        void devuelvePaginaMapeada() {
            Inscripcion inscripcion = inscripcionConfirmada();
            when(inscripcionRepository.listarDeUsuario(eq(ID_USUARIO), any()))
                    .thenReturn(new PageImpl<>(List.of(inscripcion), PageRequest.of(0, 10), 1));
            when(historialRepository.buscarPorInscripciones(List.of(ID_INSCRIPCION))).thenReturn(List.of());

            var pagina = servicio.historial(EMAIL, PageRequest.of(0, 10));

            assertThat(pagina.getContent()).hasSize(1);
            assertThat(pagina.getContent().get(0).getIdInscripcion()).isEqualTo(ID_INSCRIPCION);
        }
    }

    // =====================================================================
    // Auxiliares
    // =====================================================================

    private Inscripcion inscripcionConfirmada() {
        return inscripcion(InscripcionEstadoNombre.CONFIRMADA);
    }

    private Inscripcion inscripcionPendientePago() {
        return inscripcion(InscripcionEstadoNombre.PENDIENTE_PAGO);
    }

    private Inscripcion inscripcion(InscripcionEstadoNombre nombreEstado) {
        Evento evento = new Evento();
        evento.setIdEvento(20L);
        evento.setNombre("Festival de Musica");

        EventoCronograma cronograma = new EventoCronograma();
        cronograma.setIdCronograma(30L);
        cronograma.setEvento(evento);
        cronograma.setFecha(LocalDate.now().plusDays(10));
        cronograma.setHoraInicio(LocalTime.of(20, 0));
        cronograma.setHoraFin(LocalTime.of(23, 0));

        TipoTicket tipoTicket = new TipoTicket();
        tipoTicket.setIdTipoTicket(40L);
        tipoTicket.setNombre("Entrada General");

        CronogramaTicket ticket = new CronogramaTicket();
        ticket.setIdCronogramaTicket(50L);
        ticket.setCronograma(cronograma);
        ticket.setTipoTicket(tipoTicket);
        ticket.setPrecio(new BigDecimal("1000.00"));
        ticket.setCupoMaximo(10);
        ticket.setCupoActual(1);

        Inscripcion inscripcion = new Inscripcion();
        inscripcion.setIdInscripcion(ID_INSCRIPCION);
        inscripcion.setCronogramaTicket(ticket);
        inscripcion.setUsuario(participante);
        inscripcion.setEstadoInscripcion(estado(nombreEstado));
        inscripcion.setFechaInscripcion(LocalDate.now());
        inscripcion.setPrecioAbonado(ticket.getPrecio());
        return inscripcion;
    }

    private InscripcionEstado estado(InscripcionEstadoNombre nombre) {
        InscripcionEstado estado = new InscripcionEstado();
        estado.setNombreEstado(nombre.name());
        return estado;
    }

    private PagoEstado estadoPago(PagoEstadoNombre nombre) {
        PagoEstado estado = new PagoEstado();
        estado.setNombreEstado(nombre.name());
        return estado;
    }
}
