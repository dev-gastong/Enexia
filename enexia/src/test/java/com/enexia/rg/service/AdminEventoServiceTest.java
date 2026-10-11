package com.enexia.rg.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.enexia.rg.dto.AdminDecisionEventoRequest;
import com.enexia.rg.dto.EventoAdminDetalleResponse;
import com.enexia.rg.exception.OperacionNoPermitidaException;
import com.enexia.rg.exception.RecursoNoEncontradoException;
import com.enexia.rg.exception.ReglaNegocioException;
import com.enexia.rg.model.Evento;
import com.enexia.rg.model.EventoEstadoOrganizador;
import com.enexia.rg.model.EventoEstadoSistema;
import com.enexia.rg.model.HistorialEstadoEvento;
import com.enexia.rg.model.Usuario;
import com.enexia.rg.repository.CronogramaTicketRepository;
import com.enexia.rg.repository.EventoDetalleRepository;
import com.enexia.rg.repository.EventoEstadoSistemaRepository;
import com.enexia.rg.repository.EventoRepository;
import com.enexia.rg.repository.HistorialEstadoEventoRepository;
import com.enexia.rg.repository.UsuarioRepository;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Pruebas de {@link AdminEventoService}: moderacion administrativa general
 * (RF-6.1) y detalle para el panel.
 *
 * Se fijan las reglas que cambiaron el 2026-10-11: la suspension exige motivo
 * Y observacion; rechazar algo ya rechazado (incluso por el sistema) es 409; y
 * los eventos en moderacion automatica (EN_PROCESO, EN_REVISION) no admiten
 * ninguna decision.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminEventoService - moderacion administrativa de eventos")
class AdminEventoServiceTest {

    @Mock private EventoRepository eventoRepository;
    @Mock private EventoEstadoSistemaRepository estadoSistemaRepository;
    @Mock private HistorialEstadoEventoRepository historialRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private EventoDetalleRepository detalleRepository;
    @Mock private CronogramaTicketRepository ticketRepository;
    @Mock private EventoService eventoService;
    @Mock private EventoMapper eventoMapper;
    @Mock private AuditoriaService auditoriaService;
    @Mock private HttpServletRequest request;

    @InjectMocks private AdminEventoService servicio;

    private static final String EMAIL_ADMIN = "admin@enexia.test";
    private static final Long ID_EVENTO = 42L;
    private static final String OBSERVACION_VALIDA = "Denuncia verificada por el equipo de moderacion";

    private Usuario administrador;

    @BeforeEach
    void prepararEscenario() {
        administrador = new Usuario();
        administrador.setIdUsuario(7L);
        when(usuarioRepository.buscarActivoPorEmailConRoles(EMAIL_ADMIN))
                .thenReturn(Optional.of(administrador));

        // El catalogo devuelve una fila por cada (estado, motivo) que se pida.
        when(estadoSistemaRepository.buscarPorEstadoYMotivo(any(), any()))
                .thenAnswer(inv -> Optional.of(fila(inv.getArgument(0), inv.getArgument(1))));
    }

    private static EventoEstadoSistema fila(String estado, String motivo) {
        EventoEstadoSistema fila = new EventoEstadoSistema();
        fila.setEstadoSistema(estado);
        fila.setMotivoCodigo(motivo);
        return fila;
    }

    private Evento eventoEn(String estado, String motivo) {
        Evento evento = new Evento();
        evento.setIdEvento(ID_EVENTO);
        evento.setEstadoSistema(fila(estado, motivo));
        EventoEstadoOrganizador publicado = new EventoEstadoOrganizador();
        publicado.setEstadoOrganizador("PUBLICADO");
        evento.setEstadoOrganizador(publicado);
        when(eventoRepository.bloquearParaActualizar(ID_EVENTO)).thenReturn(Optional.of(evento));
        return evento;
    }

    private static AdminDecisionEventoRequest rechazar(String motivo, String observacion) {
        AdminDecisionEventoRequest datos = new AdminDecisionEventoRequest();
        datos.setDecision("RECHAZAR");
        datos.setMotivoCodigo(motivo);
        datos.setObservacion(observacion);
        return datos;
    }

    private static AdminDecisionEventoRequest aprobar() {
        AdminDecisionEventoRequest datos = new AdminDecisionEventoRequest();
        datos.setDecision("APROBAR");
        return datos;
    }

    @Nested
    @DisplayName("Suspension disciplinaria (RF-6.1b)")
    class Suspension {

        @Test
        @DisplayName("Un evento aprobado pasa a RECHAZADO_MANUAL con motivo y la observacion queda en el historial")
        void suspendeYGuardaObservacion() {
            Evento evento = eventoEn("APROBADO_SISTEMA", null);

            servicio.decidir(EMAIL_ADMIN, ID_EVENTO,
                    rechazar("DENUNCIA_FUNDADA", "  " + OBSERVACION_VALIDA + "  "), request);

            assertThat(evento.getEstadoSistema().getEstadoSistema()).isEqualTo("RECHAZADO_MANUAL");
            assertThat(evento.getEstadoSistema().getMotivoCodigo()).isEqualTo("DENUNCIA_FUNDADA");

            ArgumentCaptor<HistorialEstadoEvento> captor = ArgumentCaptor.forClass(HistorialEstadoEvento.class);
            verify(historialRepository).save(captor.capture());
            assertThat(captor.getValue().getObservacion()).isEqualTo(OBSERVACION_VALIDA);
            assertThat(captor.getValue().getUsuario()).isSameAs(administrador);
        }

        @Test
        @DisplayName("Tambien se puede suspender un APROBADO_MANUAL")
        void suspendeAprobadoManual() {
            Evento evento = eventoEn("APROBADO_MANUAL", null);

            servicio.decidir(EMAIL_ADMIN, ID_EVENTO,
                    rechazar("FRAUDE_DETECTADO", OBSERVACION_VALIDA), request);

            assertThat(evento.getEstadoSistema().getEstadoSistema()).isEqualTo("RECHAZADO_MANUAL");
        }

        @Test
        @DisplayName("Sin observacion no suspende y no toca nada")
        void sinObservacion() {
            eventoEn("APROBADO_SISTEMA", null);

            assertThatThrownBy(() -> servicio.decidir(EMAIL_ADMIN, ID_EVENTO,
                    rechazar("DENUNCIA_FUNDADA", null), request))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("observacion");

            verify(eventoRepository, never()).save(any());
            verify(historialRepository, never()).save(any());
        }

        @Test
        @DisplayName("Una observacion de solo espacios cuenta como vacia")
        void observacionEnBlanco() {
            eventoEn("APROBADO_SISTEMA", null);

            assertThatThrownBy(() -> servicio.decidir(EMAIL_ADMIN, ID_EVENTO,
                    rechazar("DENUNCIA_FUNDADA", "            "), request))
                    .isInstanceOf(ReglaNegocioException.class);
        }

        @Test
        @DisplayName("Una observacion de menos de 10 caracteres se rechaza")
        void observacionCorta() {
            eventoEn("APROBADO_SISTEMA", null);

            assertThatThrownBy(() -> servicio.decidir(EMAIL_ADMIN, ID_EVENTO,
                    rechazar("DENUNCIA_FUNDADA", "corta"), request))
                    .isInstanceOf(ReglaNegocioException.class);
        }

        @Test
        @DisplayName("Una observacion de mas de 500 caracteres se rechaza")
        void observacionLarga() {
            eventoEn("APROBADO_SISTEMA", null);

            assertThatThrownBy(() -> servicio.decidir(EMAIL_ADMIN, ID_EVENTO,
                    rechazar("DENUNCIA_FUNDADA", "x".repeat(501)), request))
                    .isInstanceOf(ReglaNegocioException.class);
        }

        @Test
        @DisplayName("Sin motivo codificado no suspende")
        void sinMotivo() {
            eventoEn("APROBADO_SISTEMA", null);

            assertThatThrownBy(() -> servicio.decidir(EMAIL_ADMIN, ID_EVENTO,
                    rechazar(null, OBSERVACION_VALIDA), request))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("motivo");
        }

        @Test
        @DisplayName("Un motivo fuera del vocabulario se rechaza")
        void motivoInvalido() {
            eventoEn("APROBADO_SISTEMA", null);

            assertThatThrownBy(() -> servicio.decidir(EMAIL_ADMIN, ID_EVENTO,
                    rechazar("PORQUE_SI", OBSERVACION_VALIDA), request))
                    .isInstanceOf(ReglaNegocioException.class);
        }
    }

    @Nested
    @DisplayName("Rechazar lo que no corresponde rechazar")
    class RechazoInvalido {

        @Test
        @DisplayName("Rechazar un RECHAZADO_SISTEMA es 409: ratificar no es una decision")
        void ratificarRechazoDelSistema() {
            Evento evento = eventoEn("RECHAZADO_SISTEMA", "MODERACION_TEXTO");

            assertThatThrownBy(() -> servicio.decidir(EMAIL_ADMIN, ID_EVENTO,
                    rechazar("DENUNCIA_FUNDADA", OBSERVACION_VALIDA), request))
                    .isInstanceOf(OperacionNoPermitidaException.class);

            assertThat(evento.getEstadoSistema().getEstadoSistema()).isEqualTo("RECHAZADO_SISTEMA");
            verify(historialRepository, never()).save(any());
        }

        @Test
        @DisplayName("Rechazar un RECHAZADO_MANUAL es 409")
        void rechazarYaSuspendido() {
            eventoEn("RECHAZADO_MANUAL", "FRAUDE_DETECTADO");

            assertThatThrownBy(() -> servicio.decidir(EMAIL_ADMIN, ID_EVENTO,
                    rechazar("FRAUDE_DETECTADO", OBSERVACION_VALIDA), request))
                    .isInstanceOf(OperacionNoPermitidaException.class);
        }

        @Test
        @DisplayName("Un evento EN_PROCESO no admite ninguna decision")
        void enProceso() {
            eventoEn("EN_PROCESO", null);

            assertThatThrownBy(() -> servicio.decidir(EMAIL_ADMIN, ID_EVENTO,
                    rechazar("DENUNCIA_FUNDADA", OBSERVACION_VALIDA), request))
                    .isInstanceOf(OperacionNoPermitidaException.class);
        }

        @Test
        @DisplayName("Un evento EN_REVISION (cambios en remoderacion) no admite ninguna decision")
        void enRevision() {
            eventoEn("EN_REVISION", null);

            assertThatThrownBy(() -> servicio.decidir(EMAIL_ADMIN, ID_EVENTO,
                    rechazar("DENUNCIA_FUNDADA", OBSERVACION_VALIDA), request))
                    .isInstanceOf(OperacionNoPermitidaException.class);
            assertThatThrownBy(() -> servicio.decidir(EMAIL_ADMIN, ID_EVENTO, aprobar(), request))
                    .isInstanceOf(OperacionNoPermitidaException.class);
        }
    }

    @Nested
    @DisplayName("Aprobar y revertir")
    class Aprobacion {

        @Test
        @DisplayName("Aprobar de todas formas un RECHAZADO_SISTEMA lo deja APROBADO_MANUAL sin motivo")
        void aprobarDeTodasFormas() {
            Evento evento = eventoEn("RECHAZADO_SISTEMA", "MODERACION_IMAGEN");

            servicio.decidir(EMAIL_ADMIN, ID_EVENTO, aprobar(), request);

            assertThat(evento.getEstadoSistema().getEstadoSistema()).isEqualTo("APROBADO_MANUAL");
            assertThat(evento.getEstadoSistema().getMotivoCodigo()).isNull();
        }

        @Test
        @DisplayName("Revertir una suspension vuelve a APROBADO_MANUAL")
        void revertirSuspension() {
            Evento evento = eventoEn("RECHAZADO_MANUAL", "DENUNCIA_FUNDADA");

            servicio.decidir(EMAIL_ADMIN, ID_EVENTO, aprobar(), request);

            assertThat(evento.getEstadoSistema().getEstadoSistema()).isEqualTo("APROBADO_MANUAL");
        }

        @Test
        @DisplayName("Aprobar no guarda observacion aunque el cliente la mande")
        void aprobarDescartaObservacion() {
            eventoEn("RECHAZADO_SISTEMA", "MODERACION_TEXTO");
            AdminDecisionEventoRequest datos = aprobar();
            datos.setObservacion("texto que nadie pidio ni reviso");

            servicio.decidir(EMAIL_ADMIN, ID_EVENTO, datos, request);

            ArgumentCaptor<HistorialEstadoEvento> captor = ArgumentCaptor.forClass(HistorialEstadoEvento.class);
            verify(historialRepository).save(captor.capture());
            assertThat(captor.getValue().getObservacion()).isNull();
        }

        @Test
        @DisplayName("Aprobar algo ya aprobado es 409")
        void yaAprobado() {
            eventoEn("APROBADO_SISTEMA", null);

            assertThatThrownBy(() -> servicio.decidir(EMAIL_ADMIN, ID_EVENTO, aprobar(), request))
                    .isInstanceOf(OperacionNoPermitidaException.class);
        }
    }

    @Nested
    @DisplayName("Detalle para el panel")
    class Detalle {

        @Test
        @DisplayName("Un evento inexistente es 404")
        void inexistente() {
            when(eventoRepository.findById(ID_EVENTO)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.obtenerDetalle(ID_EVENTO))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }

        @Test
        @DisplayName("Un evento suspendido expone motivo, observacion y fecha de la ultima decision")
        void suspendidoConObservacion() {
            Evento evento = new Evento();
            evento.setIdEvento(ID_EVENTO);
            evento.setNombre("Torneo");
            evento.setEstadoSistema(fila("RECHAZADO_MANUAL", "FRAUDE_DETECTADO"));
            when(eventoRepository.findById(ID_EVENTO)).thenReturn(Optional.of(evento));
            when(detalleRepository.buscarConUbicacion(ID_EVENTO)).thenReturn(Optional.empty());
            when(ticketRepository.sumarCupoMaximo(ID_EVENTO)).thenReturn(150L);

            LocalDateTime cuando = LocalDateTime.of(2026, 10, 11, 0, 27);
            HistorialEstadoEvento ultimo = new HistorialEstadoEvento();
            ultimo.setObservacion(OBSERVACION_VALIDA);
            ultimo.setFechaCambio(cuando);
            when(historialRepository.findFirstByEventoIdEventoOrderByFechaCambioDescIdHistorialDesc(ID_EVENTO))
                    .thenReturn(Optional.of(ultimo));

            EventoAdminDetalleResponse detalle = servicio.obtenerDetalle(ID_EVENTO);

            assertThat(detalle.getEstadoSistema()).isEqualTo("RECHAZADO_MANUAL");
            assertThat(detalle.getMotivoCodigo()).isEqualTo("FRAUDE_DETECTADO");
            assertThat(detalle.getObservacionAdmin()).isEqualTo(OBSERVACION_VALIDA);
            assertThat(detalle.getFechaUltimaDecision()).isEqualTo(cuando);
            assertThat(detalle.getCupoMaximo()).isEqualTo(150L);
        }

        @Test
        @DisplayName("Un evento sin historial ni detalle carga igual, con los datos opcionales en null")
        void sinHistorialNiDetalle() {
            Evento evento = new Evento();
            evento.setIdEvento(ID_EVENTO);
            evento.setEstadoSistema(fila("APROBADO_SISTEMA", null));
            when(eventoRepository.findById(ID_EVENTO)).thenReturn(Optional.of(evento));
            when(detalleRepository.buscarConUbicacion(eq(ID_EVENTO))).thenReturn(Optional.empty());
            when(historialRepository.findFirstByEventoIdEventoOrderByFechaCambioDescIdHistorialDesc(ID_EVENTO))
                    .thenReturn(Optional.empty());

            EventoAdminDetalleResponse detalle = servicio.obtenerDetalle(ID_EVENTO);

            assertThat(detalle.getObservacionAdmin()).isNull();
            assertThat(detalle.getCiudad()).isNull();
            assertThat(detalle.getNombre()).isEqualTo("(Evento en validacion)");
        }
    }
}
