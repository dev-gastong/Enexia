package com.enexia.rg.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.enexia.rg.dto.CronogramaRequest;
import com.enexia.rg.dto.EventoCrearRequest;
import com.enexia.rg.dto.TicketRequest;
import com.enexia.rg.dto.UbicacionRequest;
import com.enexia.rg.event.EventoCreadoEvent;
import com.enexia.rg.exception.OperacionNoPermitidaException;
import com.enexia.rg.exception.RecursoNoEncontradoException;
import com.enexia.rg.exception.ReglaNegocioException;
import com.enexia.rg.model.EstadoEventoOrganizadorNombre;
import com.enexia.rg.model.EstadoEventoSistemaNombre;
import com.enexia.rg.model.Evento;
import com.enexia.rg.model.EventoEstadoOrganizador;
import com.enexia.rg.model.EventoEstadoSistema;
import com.enexia.rg.model.Rol;
import com.enexia.rg.model.Usuario;
import com.enexia.rg.model.UsuarioRol;
import com.enexia.rg.repository.CategoriaRepository;
import com.enexia.rg.repository.CronogramaTicketRepository;
import com.enexia.rg.repository.EventoCronogramaRepository;
import com.enexia.rg.repository.EventoDetalleRepository;
import com.enexia.rg.repository.EventoEstadoOrganizadorRepository;
import com.enexia.rg.repository.EventoEstadoSistemaRepository;
import com.enexia.rg.repository.EventoRepository;
import com.enexia.rg.repository.HistorialEstadoEventoRepository;
import com.enexia.rg.repository.UsuarioRepository;
import com.enexia.rg.repository.VisitaRepository;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Pruebas de la FASE SINCRONA de creacion de eventos (DFD 2.0 a 2.4) y de la
 * baja logica (RF-2.9).
 *
 * DOS COSAS SE VERIFICAN CON PARTICULAR CUIDADO:
 *
 *   1. QUE SE GUARDA EN EL SKELETON. RF-2.2 es explicito: al crear NO se
 *      persiste contenido (titulo, descripcion, agenda, imagenes). Si un cambio
 *      futuro empezara a guardar el titulo "porque es comodo para el
 *      dashboard", se estaria rompiendo la regla arquitectonica de moderar antes
 *      de persistir, y esta suite lo detecta.
 *
 *   2. QUE EL PIPELINE SE DISPARA POR EVENTO Y NO POR LLAMADA DIRECTA. Es lo que
 *      evita la condicion de carrera con el commit (ver EventoCreadoEvent).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EventoService - creacion y baja de eventos (RF-2.1 a RF-2.9)")
class EventoServiceTest {

    @Mock private EventoRepository eventoRepository;
    @Mock private EventoDetalleRepository eventoDetalleRepository;
    @Mock private EventoCronogramaRepository cronogramaRepository;
    @Mock private CronogramaTicketRepository ticketRepository;
    @Mock private EventoEstadoSistemaRepository estadoSistemaRepository;
    @Mock private EventoEstadoOrganizadorRepository estadoOrganizadorRepository;
    @Mock private HistorialEstadoEventoRepository historialRepository;
    @Mock private CategoriaRepository categoriaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private VisitaRepository visitaRepository;

    @Mock private PersonaJuridicaService personaJuridicaService;
    @Mock private ApplicationEventPublisher publicadorDeEventos;
    @Mock private AuditoriaService auditoriaService;

    @Mock private HttpServletRequest request;

    private final EventoMapper eventoMapper = new EventoMapper();

    @InjectMocks private EventoService servicio;

    private static final String EMAIL = "organizador@enexia.test";

    private EventoCrearRequest datos;
    private List<ImagenPendiente> imagenes;

    @BeforeEach
    void prepararEscenario() {
        // El mapper es logica pura sin dependencias: se usa el real en vez de un
        // mock. Mockearlo obligaria a repetir en cada test que devuelve, y no
        // detectaria un error en el armado de la respuesta.
        ReflectionTestUtils.setField(servicio, "eventoMapper", eventoMapper);
        // Los @Value no los resuelve Mockito: sin esto quedarian en 0 y el
        // limite de plan rechazaria hasta el primer evento.
        ReflectionTestUtils.setField(servicio, "limitePlanGratuito", 20);
        ReflectionTestUtils.setField(servicio, "maxImagenes", 3);

        datos = peticionValida();
        imagenes = List.of(new ImagenPendiente("portada.png", new byte[]{1, 2, 3}));

        when(usuarioRepository.buscarActivoPorEmailConRoles(EMAIL))
                .thenReturn(Optional.of(organizador()));
        when(categoriaRepository.existsById(anyLong())).thenReturn(true);
        when(eventoRepository.contarVigentesDeOrganizador(anyLong(), anyString())).thenReturn(0L);
        when(estadoSistemaRepository.buscarPorEstadoYMotivo(
                EstadoEventoSistemaNombre.EN_PROCESO.name(), null))
                .thenReturn(Optional.of(estadoSistema(EstadoEventoSistemaNombre.EN_PROCESO)));
        when(estadoOrganizadorRepository.findByEstadoOrganizadorIgnoreCase(
                EstadoEventoOrganizadorNombre.PUBLICADO.name()))
                .thenReturn(Optional.of(estadoOrganizador(EstadoEventoOrganizadorNombre.PUBLICADO)));
        when(eventoRepository.save(any())).thenAnswer(i -> {
            Evento evento = i.getArgument(0);
            evento.setIdEvento(99L);
            return evento;
        });
    }

    // =====================================================================
    // Creacion
    // =====================================================================

    @Nested
    @DisplayName("Skeleton EN_PROCESO (paso 2.4 del DFD)")
    class Skeleton {

        @Test
        @DisplayName("El evento nace EN_PROCESO y PUBLICADO, con fecha de creacion")
        void naceEnProceso() {
            ArgumentCaptor<Evento> captor = ArgumentCaptor.forClass(Evento.class);

            servicio.crear(EMAIL, datos, imagenes, request);

            verify(eventoRepository).save(captor.capture());
            Evento guardado = captor.getValue();

            assertThat(guardado.getEstadoSistema().getEstadoSistema())
                    .isEqualTo(EstadoEventoSistemaNombre.EN_PROCESO.name());
            assertThat(guardado.getEstadoOrganizador().getEstadoOrganizador())
                    .isEqualTo(EstadoEventoOrganizadorNombre.PUBLICADO.name());
            // RF-2.2 la exige de forma literal: sin ella el dashboard no puede
            // ordenar ni detectar eventos atascados en EN_PROCESO.
            assertThat(guardado.getFechaCreacion()).isNotNull();
        }

        @Test
        @DisplayName("NO se persiste contenido: ni titulo, ni categoria, ni portada (RF-2.2)")
        void noPersisteContenido() {
            ArgumentCaptor<Evento> captor = ArgumentCaptor.forClass(Evento.class);

            servicio.crear(EMAIL, datos, imagenes, request);

            verify(eventoRepository).save(captor.capture());
            Evento guardado = captor.getValue();

            // Es la regla arquitectonica del proyecto: moderar ANTES de
            // persistir contenido. Si el titulo se guardara aca, un titulo
            // ofensivo quedaria en la base aunque despues se rechace.
            assertThat(guardado.getNombre()).isNull();
            assertThat(guardado.getCategoria()).isNull();
            assertThat(guardado.getUrlPortada()).isNull();

            // Y nada de la agenda ni del detalle toca la base todavia.
            verify(eventoDetalleRepository, never()).save(any());
            verify(cronogramaRepository, never()).save(any());
            verify(ticketRepository, never()).save(any());
        }

        @Test
        @DisplayName("El pipeline se dispara PUBLICANDO un evento, no llamando al service")
        void disparaPorEvento() {
            ArgumentCaptor<EventoCreadoEvent> captor =
                    ArgumentCaptor.forClass(EventoCreadoEvent.class);

            servicio.crear(EMAIL, datos, imagenes, request);

            verify(publicadorDeEventos).publishEvent(captor.capture());
            EventoCreadoEvent aviso = captor.getValue();

            // La diferencia no es de estilo. Una llamada directa arrancaria el
            // hilo de moderacion con el INSERT sin confirmar, y ese hilo no
            // encontraria el evento. Ya paso: terminaba en ERROR_PIPELINE.
            assertThat(aviso.idEvento()).isEqualTo(99L);
            assertThat(aviso.datos()).isSameAs(datos);
            assertThat(aviso.imagenes()).isSameAs(imagenes);
            assertThat(aviso.emailOrganizador()).isEqualTo(EMAIL);
        }
    }

    @Nested
    @DisplayName("Validaciones que @Valid no puede hacer (paso 2.2 y 2.3)")
    class Validaciones {

        @Test
        @DisplayName("Hora de fin anterior a la de inicio -> 400")
        void horaFinAnterior() {
            datos.getCronogramas().get(0).setHoraInicio(LocalTime.of(22, 0));
            datos.getCronogramas().get(0).setHoraFin(LocalTime.of(20, 0));

            assertThatThrownBy(() -> servicio.crear(EMAIL, datos, imagenes, request))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("posterior");

            verify(eventoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Duracion cero tampoco se admite")
        void duracionCero() {
            datos.getCronogramas().get(0).setHoraInicio(LocalTime.of(20, 0));
            datos.getCronogramas().get(0).setHoraFin(LocalTime.of(20, 0));

            assertThatThrownBy(() -> servicio.crear(EMAIL, datos, imagenes, request))
                    .isInstanceOf(ReglaNegocioException.class);
        }

        @Test
        @DisplayName("Dos funciones con la misma fecha y hora -> 400")
        void fechasDuplicadas() {
            CronogramaRequest repetida = cronograma(LocalDate.now().plusDays(30), LocalTime.of(18, 0));
            datos.setCronogramas(List.of(
                    cronograma(LocalDate.now().plusDays(30), LocalTime.of(18, 0)), repetida));

            // Suele ser un doble clic en "agregar fecha". Dejarlo pasar
            // duplicaria los tickets y confundiria al participante en la ficha.
            assertThatThrownBy(() -> servicio.crear(EMAIL, datos, imagenes, request))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("misma fecha");
        }

        @Test
        @DisplayName("Sin imagenes -> 400 (DFD 5.2.0 exige entre 1 y 3)")
        void sinImagenes() {
            assertThatThrownBy(() -> servicio.crear(EMAIL, datos, List.of(), request))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("al menos una imagen");
        }

        @Test
        @DisplayName("Mas de 3 imagenes -> 400")
        void demasiadasImagenes() {
            ImagenPendiente img = new ImagenPendiente("a.png", new byte[]{1});

            assertThatThrownBy(() ->
                    servicio.crear(EMAIL, datos, List.of(img, img, img, img), request))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("maximo");
        }

        @Test
        @DisplayName("Categoria inexistente -> 400 con mensaje accionable")
        void categoriaInexistente() {
            when(categoriaRepository.existsById(anyLong())).thenReturn(false);

            assertThatThrownBy(() -> servicio.crear(EMAIL, datos, imagenes, request))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("categoria");
        }

        @Test
        @DisplayName("Limite del plan alcanzado -> 409 (paso 2.1 del DFD)")
        void limiteDePlan() {
            when(eventoRepository.contarVigentesDeOrganizador(anyLong(), anyString())).thenReturn(20L);

            assertThatThrownBy(() -> servicio.crear(EMAIL, datos, imagenes, request))
                    .isInstanceOf(OperacionNoPermitidaException.class)
                    .hasMessageContaining("limite");
        }
    }

    // =====================================================================
    // Baja logica (RF-2.9)
    // =====================================================================

    @Nested
    @DisplayName("Baja logica")
    class Baja {

        @Test
        @DisplayName("Muta el estado a DADO_DE_BAJA sin borrar la fila")
        void bajaLogica() {
            Evento evento = eventoPropio();
            when(eventoRepository.buscarConAsociaciones(99L)).thenReturn(Optional.of(evento));
            // La baja relee con bloqueo de fila antes de escribir.
            when(eventoRepository.bloquearParaActualizar(99L)).thenReturn(Optional.of(evento));
            when(estadoOrganizadorRepository.findByEstadoOrganizadorIgnoreCase(
                    EstadoEventoOrganizadorNombre.DADO_DE_BAJA.name()))
                    .thenReturn(Optional.of(estadoOrganizador(EstadoEventoOrganizadorNombre.DADO_DE_BAJA)));

            servicio.darDeBaja(EMAIL, 99L, request);

            assertThat(evento.getEstadoOrganizador().getEstadoOrganizador())
                    .isEqualTo(EstadoEventoOrganizadorNombre.DADO_DE_BAJA.name());
            // Borrar de verdad romperia inscripciones, pagos y valoraciones que
            // apuntan a este evento.
            verify(eventoRepository, never()).delete(any());
            verify(historialRepository).save(any());
        }

        @Test
        @DisplayName("Evento de otro organizador -> 404, no 403")
        void eventoAjeno() {
            Evento ajeno = eventoPropio();
            Usuario otro = new Usuario();
            otro.setIdUsuario(999L);
            ajeno.setOrganizador(otro);
            when(eventoRepository.buscarConAsociaciones(99L)).thenReturn(Optional.of(ajeno));

            // Un 403 confirmaria que ese id existe; iterando ids se podria
            // mapear cuantos eventos hay y de quien son.
            assertThatThrownBy(() -> servicio.darDeBaja(EMAIL, 99L, request))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }

        @Test
        @DisplayName("Dar de baja dos veces -> 409")
        void bajaRepetida() {
            Evento evento = eventoPropio();
            evento.setEstadoOrganizador(estadoOrganizador(EstadoEventoOrganizadorNombre.DADO_DE_BAJA));
            when(eventoRepository.buscarConAsociaciones(99L)).thenReturn(Optional.of(evento));
            when(eventoRepository.bloquearParaActualizar(99L)).thenReturn(Optional.of(evento));

            assertThatThrownBy(() -> servicio.darDeBaja(EMAIL, 99L, request))
                    .isInstanceOf(OperacionNoPermitidaException.class);
        }
    }

    // =====================================================================
    // Auxiliares
    // =====================================================================

    private EventoCrearRequest peticionValida() {
        EventoCrearRequest peticion = new EventoCrearRequest();
        peticion.setNombre("Festival de Invierno Fueguino");
        peticion.setDescripcion("Tres jornadas de musica en vivo y gastronomia regional en Ushuaia.");
        peticion.setIdCategoria(1L);

        UbicacionRequest ubicacion = new UbicacionRequest();
        ubicacion.setCalle("Maipu");
        ubicacion.setNumeroExterior("505");
        ubicacion.setIdCiudad(13L);
        peticion.setUbicacion(ubicacion);

        peticion.setCronogramas(List.of(
                cronograma(LocalDate.now().plusDays(30), LocalTime.of(18, 0))));
        return peticion;
    }

    private CronogramaRequest cronograma(LocalDate fecha, LocalTime inicio) {
        TicketRequest ticket = new TicketRequest();
        ticket.setTipoTicket("Inscripcion General");
        ticket.setPrecio(BigDecimal.ZERO);
        ticket.setCupoMaximo(200);

        CronogramaRequest cronograma = new CronogramaRequest();
        cronograma.setFecha(fecha);
        cronograma.setHoraInicio(inicio);
        cronograma.setHoraFin(inicio.plusHours(5));
        cronograma.setTickets(List.of(ticket));
        return cronograma;
    }

    private Usuario organizador() {
        Rol rol = new Rol();
        rol.setNombreRol("ORGANIZADOR");

        Usuario usuario = new Usuario();
        usuario.setIdUsuario(1L);
        usuario.setEmail(EMAIL);
        usuario.setNickname("organizador");

        UsuarioRol usuarioRol = new UsuarioRol();
        usuarioRol.setUsuario(usuario);
        usuarioRol.setRol(rol);
        usuario.setUsuarioRoles(Set.of(usuarioRol));
        return usuario;
    }

    private Evento eventoPropio() {
        Evento evento = new Evento();
        evento.setIdEvento(99L);
        evento.setOrganizador(organizador());
        evento.setEstadoSistema(estadoSistema(EstadoEventoSistemaNombre.APROBADO_SISTEMA));
        evento.setEstadoOrganizador(estadoOrganizador(EstadoEventoOrganizadorNombre.PUBLICADO));
        return evento;
    }

    private EventoEstadoSistema estadoSistema(EstadoEventoSistemaNombre nombre) {
        EventoEstadoSistema estado = new EventoEstadoSistema();
        estado.setEstadoSistema(nombre.name());
        return estado;
    }

    private EventoEstadoOrganizador estadoOrganizador(EstadoEventoOrganizadorNombre nombre) {
        EventoEstadoOrganizador estado = new EventoEstadoOrganizador();
        estado.setEstadoOrganizador(nombre.name());
        return estado;
    }
}
