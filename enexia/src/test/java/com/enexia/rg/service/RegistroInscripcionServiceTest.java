package com.enexia.rg.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
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

import com.enexia.rg.exception.OperacionNoPermitidaException;
import com.enexia.rg.exception.RecursoDuplicadoException;
import com.enexia.rg.exception.RecursoNoEncontradoException;
import com.enexia.rg.model.CronogramaTicket;
import com.enexia.rg.model.Inscripcion;
import com.enexia.rg.model.InscripcionEstado;
import com.enexia.rg.model.InscripcionEstadoNombre;
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

/**
 * Pruebas de la fase atomica de reserva (RF-3.1, RF-3.2; DFD 3.1.1 a 3.2.3).
 *
 * El foco particular de esta suite es el camino B.1 (pago rechazado): que la
 * inscripcion y el pago FALLIDO se persistan de verdad (el metodo NO tiene
 * que lanzar excepcion) es el contrato completo que justifica separar esta
 * clase de {@code InscripcionService} -- ver el javadoc de la clase bajo prueba.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RegistroInscripcionService - reserva de tickets (RF-3.1, RF-3.2)")
class RegistroInscripcionServiceTest {

    @Mock private InscripcionRepository inscripcionRepository;
    @Mock private InscripcionEstadoRepository inscripcionEstadoRepository;
    @Mock private CronogramaTicketRepository ticketRepository;
    @Mock private PagoRepository pagoRepository;
    @Mock private PagoEstadoRepository pagoEstadoRepository;
    @Mock private HistorialEstadoInscripcionRepository historialRepository;
    @Mock private AuditoriaService auditoriaService;
    @Mock private PasarelaPagoSimuladaService pasarela;
    @Mock private HttpServletRequest request;

    @InjectMocks private RegistroInscripcionService servicio;

    private static final Long ID_TICKET = 5L;
    private static final Long ID_CRONOGRAMA = 30L;

    private Usuario participante;

    @BeforeEach
    void prepararEscenario() {
        participante = new Usuario();
        participante.setIdUsuario(1L);
        participante.setEmail("participante@enexia.test");

        // save() sin identidad todavia: se le asigna un id fijo, como haria la
        // base con GenerationType.IDENTITY.
        when(inscripcionRepository.save(any(Inscripcion.class))).thenAnswer(inv -> {
            Inscripcion i = inv.getArgument(0);
            if (i.getIdInscripcion() == null) {
                i.setIdInscripcion(100L);
            }
            return i;
        });

        when(inscripcionEstadoRepository.findByNombreEstadoIgnoreCase(InscripcionEstadoNombre.PENDIENTE.name()))
                .thenReturn(Optional.of(estadoInscripcion(InscripcionEstadoNombre.PENDIENTE)));
        when(inscripcionEstadoRepository.findByNombreEstadoIgnoreCase(InscripcionEstadoNombre.CONFIRMADA.name()))
                .thenReturn(Optional.of(estadoInscripcion(InscripcionEstadoNombre.CONFIRMADA)));
        when(inscripcionEstadoRepository.findByNombreEstadoIgnoreCase(InscripcionEstadoNombre.PENDIENTE_PAGO.name()))
                .thenReturn(Optional.of(estadoInscripcion(InscripcionEstadoNombre.PENDIENTE_PAGO)));
        when(pagoEstadoRepository.findByNombreEstadoIgnoreCase(PagoEstadoNombre.COMPLETADO.name()))
                .thenReturn(Optional.of(estadoPago(PagoEstadoNombre.COMPLETADO)));
        when(pagoEstadoRepository.findByNombreEstadoIgnoreCase(PagoEstadoNombre.FALLIDO.name()))
                .thenReturn(Optional.of(estadoPago(PagoEstadoNombre.FALLIDO)));
    }

    @Nested
    @DisplayName("RF-3.1: ticket gratuito")
    class TicketGratuito {

        @Test
        @DisplayName("Confirma directo y suma un cupo, sin pasar por la pasarela")
        void confirmaDirecto() {
            CronogramaTicket ticket = ticket(BigDecimal.ZERO, 10, 3);
            when(ticketRepository.bloquearParaActualizar(ID_TICKET)).thenReturn(Optional.of(ticket));

            RegistroInscripcionService.ResultadoRegistro resultado =
                    servicio.registrar(participante, ID_TICKET, null, request);

            assertThat(resultado.aprobado()).isTrue();
            assertThat(ticket.getCupoActual()).isEqualTo(4);
            verify(pasarela, never()).procesar(any(), anyString());
            verify(ticketRepository).save(ticket);
        }

        @Test
        @DisplayName("Cupo agotado: rechaza antes de crear nada")
        void rechazaCupoAgotado() {
            CronogramaTicket ticket = ticket(BigDecimal.ZERO, 5, 5);
            when(ticketRepository.bloquearParaActualizar(ID_TICKET)).thenReturn(Optional.of(ticket));

            assertThatThrownBy(() -> servicio.registrar(participante, ID_TICKET, null, request))
                    .isInstanceOf(OperacionNoPermitidaException.class);

            verify(inscripcionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Ticket inexistente: 404")
        void rechazaTicketInexistente() {
            when(ticketRepository.bloquearParaActualizar(ID_TICKET)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.registrar(participante, ID_TICKET, null, request))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }

        @Test
        @DisplayName("Ya tiene una inscripcion activa en ese cronograma: rechaza antes de crear otra")
        void rechazaInscripcionDuplicada() {
            CronogramaTicket ticket = ticket(BigDecimal.ZERO, 10, 3);
            when(ticketRepository.bloquearParaActualizar(ID_TICKET)).thenReturn(Optional.of(ticket));
            when(inscripcionRepository.existeActivaDeUsuarioEnCronograma(participante.getIdUsuario(), ID_CRONOGRAMA))
                    .thenReturn(true);

            assertThatThrownBy(() -> servicio.registrar(participante, ID_TICKET, null, request))
                    .isInstanceOf(RecursoDuplicadoException.class);

            verify(inscripcionRepository, never()).save(any());
            // El cupo tampoco se toca: el rechazo es antes de cualquier escritura.
            assertThat(ticket.getCupoActual()).isEqualTo(3);
        }

        @Test
        @DisplayName("El cronograma ya finalizo: rechaza antes de crear la inscripcion")
        void rechazaEventoFinalizado() {
            CronogramaTicket ticket = ticket(BigDecimal.ZERO, 10, 3);
            ticket.getCronograma().setFecha(LocalDate.now().minusDays(1));
            ticket.getCronograma().setHoraFin(LocalTime.of(20, 0));
            when(ticketRepository.bloquearParaActualizar(ID_TICKET)).thenReturn(Optional.of(ticket));

            assertThatThrownBy(() -> servicio.registrar(participante, ID_TICKET, null, request))
                    .isInstanceOf(OperacionNoPermitidaException.class);

            verify(inscripcionRepository, never()).save(any());
            assertThat(ticket.getCupoActual()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("RF-3.2: ticket de pago")
    class TicketDePago {

        @Test
        @DisplayName("Pago aprobado: crea el Pago COMPLETADO, confirma y suma cupo")
        void pagoAprobado() {
            CronogramaTicket ticket = ticket(new BigDecimal("500.00"), 10, 0);
            when(ticketRepository.bloquearParaActualizar(ID_TICKET)).thenReturn(Optional.of(ticket));
            when(pasarela.procesar(any(), anyString()))
                    .thenReturn(PasarelaPagoSimuladaService.ResultadoPago.aprobado("tok-1"));

            RegistroInscripcionService.ResultadoRegistro resultado =
                    servicio.registrar(participante, ID_TICKET, "tarjeta", request);

            assertThat(resultado.aprobado()).isTrue();
            assertThat(ticket.getCupoActual()).isEqualTo(1);
            verify(pagoRepository).save(argThat(pago ->
                    pago.getEstadoPago().getNombreEstado().equals("COMPLETADO")));
        }

        @Test
        @DisplayName("Pago rechazado: NO lanza -- persiste Pago FALLIDO e inscripcion PENDIENTE_PAGO")
        void pagoRechazadoPersisteYNoLanza() {
            CronogramaTicket ticket = ticket(new BigDecimal("500.00"), 10, 0);
            when(ticketRepository.bloquearParaActualizar(ID_TICKET)).thenReturn(Optional.of(ticket));
            when(pasarela.procesar(any(), anyString()))
                    .thenReturn(PasarelaPagoSimuladaService.ResultadoPago.rechazado("tok-2", "rechazado"));

            RegistroInscripcionService.ResultadoRegistro resultado =
                    servicio.registrar(participante, ID_TICKET, "rechazar", request);

            assertThat(resultado.aprobado()).isFalse();
            assertThat(resultado.motivoRechazo()).isEqualTo("rechazado");
            // El cupo NUNCA se toco: solo se incrementa al confirmar (RF-3.1/3.2).
            assertThat(ticket.getCupoActual()).isZero();
            verify(ticketRepository, never()).save(any());

            verify(pagoRepository).save(argThat(pago ->
                    pago.getEstadoPago().getNombreEstado().equals("FALLIDO")));
            verify(inscripcionRepository, times(2)).save(any());
            verify(auditoriaService).registrar(
                    eq(participante), eq(AuditoriaService.ACCION_PAGO_RECHAZADO), anyString(), eq(request));
        }
    }

    // =====================================================================
    // Auxiliares
    // =====================================================================

    private CronogramaTicket ticket(BigDecimal precio, int cupoMaximo, int cupoActual) {
        com.enexia.rg.model.EventoCronograma cronograma = new com.enexia.rg.model.EventoCronograma();
        cronograma.setIdCronograma(ID_CRONOGRAMA);
        // Vigente por defecto: la mayoria de los tests no le interesa la fecha,
        // solo al de rechazaEventoFinalizado, que la pisa explicitamente.
        cronograma.setFecha(LocalDate.now().plusDays(1));
        cronograma.setHoraFin(LocalTime.of(23, 0));

        CronogramaTicket ticket = new CronogramaTicket();
        ticket.setIdCronogramaTicket(ID_TICKET);
        ticket.setCronograma(cronograma);
        ticket.setPrecio(precio);
        ticket.setCupoMaximo(cupoMaximo);
        ticket.setCupoActual(cupoActual);
        return ticket;
    }

    private InscripcionEstado estadoInscripcion(InscripcionEstadoNombre nombre) {
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
