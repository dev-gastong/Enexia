package com.enexia.rg.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.enexia.rg.dto.CronogramaRequest;
import com.enexia.rg.dto.EventoCrearRequest;
import com.enexia.rg.dto.EventoEstadisticasResponse;
import com.enexia.rg.dto.EventoResponse;
import com.enexia.rg.event.EventoCreadoEvent;
import com.enexia.rg.exception.OperacionNoPermitidaException;
import com.enexia.rg.exception.RecursoNoEncontradoException;
import com.enexia.rg.exception.ReglaNegocioException;
import com.enexia.rg.model.EstadoEventoOrganizadorNombre;
import com.enexia.rg.model.EstadoEventoSistemaNombre;
import com.enexia.rg.model.Evento;
import com.enexia.rg.model.EventoDetalle;
import com.enexia.rg.model.EventoEstadoOrganizador;
import com.enexia.rg.model.EventoEstadoSistema;
import com.enexia.rg.model.HistorialEstadoEvento;
import com.enexia.rg.model.PersonaJuridica;
import com.enexia.rg.model.Usuario;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Modulo 2: gestion de eventos por parte del organizador.
 *
 * FASE SINCRONA DEL DFD DE CREACION (pasos 2.0 a 2.4)
 * Este service hace SOLO lo rapido: validar y guardar el "skeleton". Todo lo que
 * puede tardar -- moderar texto, subir imagenes -- se delega a
 * ModeracionEventoService, que corre en otro hilo. El organizador recibe su
 * respuesta en milisegundos y ve el evento en el dashboard como
 * "Validando contenido...".
 *
 * QUE SE GUARDA EN EL SKELETON Y QUE NO
 * RF-2.2 enumera cuatro campos: id, id_organizador, estado_sistema y
 * fecha_creacion. Se agregan dos que NO son contenido moderable sino atribucion:
 *   - id_persona_juridica: bajo que organizacion se publica (RF-2.1). Se valida
 *     antes, porque si el usuario no es miembro no hay evento que crear.
 *   - id_estado_organizador = PUBLICADO: la intencion del organizador, necesaria
 *     para que el dashboard pueda filtrar (RF-2.8) desde el primer momento.
 * El titulo, la descripcion, la ubicacion, la agenda, los tickets y las imagenes
 * NO se guardan hasta que la moderacion apruebe.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventoService {

    private final EventoRepository eventoRepository;
    private final EventoDetalleRepository eventoDetalleRepository;
    private final EventoCronogramaRepository cronogramaRepository;
    private final CronogramaTicketRepository ticketRepository;
    private final EventoEstadoSistemaRepository estadoSistemaRepository;
    private final EventoEstadoOrganizadorRepository estadoOrganizadorRepository;
    private final HistorialEstadoEventoRepository historialRepository;
    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final VisitaRepository visitaRepository;

    private final PersonaJuridicaService personaJuridicaService;
    private final ApplicationEventPublisher publicadorDeEventos;
    private final AuditoriaService auditoriaService;
    private final EventoMapper eventoMapper;

    /**
     * Tope de eventos vigentes del plan gratuito (DFD 2.1).
     *
     * El control de suscripciones completo es el Modulo 8, todavia sin
     * implementar. Se deja el limite como propiedad para que el paso 2.1 del DFD
     * exista de verdad desde ahora: cuando llegue el Modulo 8, lo unico que
     * cambia es de donde sale el numero.
     */
    @Value("${enexia.eventos.limite-plan-gratuito:20}")
    private int limitePlanGratuito;

    @Value("${enexia.multimedia.max-imagenes:3}")
    private int maxImagenes;

    // =====================================================================
    // CREACION  (RF-2.1 a RF-2.6; DFD 2.0 a 2.4)
    // =====================================================================

    /**
     * Crea el evento en estado EN_PROCESO y dispara la moderacion.
     *
     * {@code @Transactional} cubre solo la fase sincrona. El pipeline asincrono
     * se lanza al final y abre sus propias transacciones: si se disparara dentro
     * de esta, podria empezar a trabajar sobre un evento que todavia no esta
     * confirmado en la base y no encontrarlo.
     */
    @Transactional
    public EventoResponse crear(String emailOrganizador,
                                EventoCrearRequest datos,
                                List<ImagenPendiente> imagenes,
                                HttpServletRequest request) {

        // --- Paso 2.0: el JWT ya lo valido el filtro; aca se resuelve la identidad.
        Usuario organizador = usuarioRepository.buscarActivoPorEmailConRoles(emailOrganizador)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro la cuenta"));

        // --- RF-2.1: bajo que identidad se publica.
        PersonaJuridica organizacion =
                personaJuridicaService.resolverOrganizacionHabilitada(organizador, datos.getIdPersonaJuridica());

        // --- Paso 2.1: limites del plan.
        verificarLimiteDePlan(organizador);

        // --- Paso 2.2: fechas y horarios.
        validarAgenda(datos.getCronogramas());

        // --- Paso 2.3: campos obligatorios que @Valid no puede comprobar
        // (existencia de la categoria en el catalogo) + cantidad de imagenes.
        if (!categoriaRepository.existsById(datos.getIdCategoria())) {
            throw new ReglaNegocioException("La categoria indicada no existe en el catalogo");
        }
        validarCantidadDeImagenes(imagenes);

        // --- Paso 2.4: skeleton EN_PROCESO.
        EventoEstadoSistema enProceso = estadoSistemaRepository
                .buscarPorEstadoYMotivo(EstadoEventoSistemaNombre.EN_PROCESO.name(), null)
                .orElseThrow(() -> new ReglaNegocioException(
                        "El catalogo evento_estado_sistema no esta inicializado"));

        EventoEstadoOrganizador publicado = estadoOrganizadorRepository
                .findByEstadoOrganizadorIgnoreCase(EstadoEventoOrganizadorNombre.PUBLICADO.name())
                .orElseThrow(() -> new ReglaNegocioException(
                        "El catalogo evento_estado_organizador no esta inicializado"));

        Evento evento = new Evento();
        evento.setOrganizador(organizador);
        evento.setPersonaJuridica(organizacion);
        evento.setEstadoSistema(enProceso);
        evento.setEstadoOrganizador(publicado);
        evento.setFechaCreacion(LocalDateTime.now());
        evento = eventoRepository.save(evento);

        registrarHistorial(evento, enProceso, publicado, organizador);

        auditoriaService.registrar(organizador, AuditoriaService.ACCION_EVENTO_CREADO,
                "Evento " + evento.getIdEvento() + " creado en EN_PROCESO"
                + (organizacion == null ? " a titulo personal"
                                        : " bajo la organizacion " + organizacion.getRazonSocial()),
                request);

        // --- Paso 2.5: disparo del pipeline asincrono.
        //
        // Se PUBLICA un evento de aplicacion en lugar de llamar al service
        // directamente, y la diferencia no es de estilo. Este metodo es
        // transaccional: una llamada directa arrancaria el hilo de moderacion
        // mientras el INSERT del evento sigue SIN CONFIRMAR, y ese hilo, en su
        // propia transaccion, no encontraria la fila. Ya paso: el evento
        // terminaba en RECHAZADO_SISTEMA/ERROR_PIPELINE por una carrera perdida.
        //
        // El listener escucha con AFTER_COMMIT, asi que no arranca hasta que
        // esta transaccion se confirmo. Como efecto util adicional, si algo
        // fallara mas abajo y la transaccion se revirtiera, el evento de
        // aplicacion NO se entrega: no queda un pipeline trabajando sobre un
        // evento que nunca existio.
        publicadorDeEventos.publishEvent(new EventoCreadoEvent(
                evento.getIdEvento(), datos, imagenes, organizador.getEmail()));

        log.info("Evento {} creado por el usuario {}; moderacion en curso",
                evento.getIdEvento(), organizador.getIdUsuario());

        return eventoMapper.aTarjeta(evento, null, null);
    }

    /**
     * Paso 2.2 del DFD: coherencia de la agenda.
     *
     * {@code @Future} en el DTO ya asegura que cada fecha sea futura. Lo que
     * ninguna anotacion puede ver es la relacion ENTRE campos y entre elementos
     * de la lista, que es exactamente lo que se valida aca.
     */
    private void validarAgenda(List<CronogramaRequest> cronogramas) {
        Set<String> vistas = new HashSet<>();

        for (CronogramaRequest cronograma : cronogramas) {
            // Hora de fin posterior a la de inicio. Se compara con isAfter y no
            // con equals: un evento de duracion cero tampoco tiene sentido.
            if (!cronograma.getHoraFin().isAfter(cronograma.getHoraInicio())) {
                throw new ReglaNegocioException(
                        "En la fecha " + cronograma.getFecha()
                        + " la hora de fin debe ser posterior a la de inicio");
            }

            // Duplicados exactos: dos funciones identicas son un error de carga
            // (doble clic en "agregar fecha"), y dejarlas pasar duplicaria los
            // tickets y confundiria al participante en la ficha publica.
            String clave = cronograma.getFecha() + "|" + cronograma.getHoraInicio();
            if (!vistas.add(clave)) {
                throw new ReglaNegocioException(
                        "Hay dos funciones con la misma fecha y hora de inicio: "
                        + cronograma.getFecha() + " " + cronograma.getHoraInicio());
            }
        }
    }

    /** DFD 5.2.0: entre 1 y el maximo configurado (3 por defecto, RF-2.3). */
    private void validarCantidadDeImagenes(List<ImagenPendiente> imagenes) {
        if (imagenes == null || imagenes.isEmpty()) {
            throw new ReglaNegocioException(
                    "Hay que cargar al menos una imagen promocional del evento");
        }
        if (imagenes.size() > maxImagenes) {
            throw new ReglaNegocioException(
                    "Se admiten como maximo " + maxImagenes + " imagenes por evento");
        }
    }

    private void verificarLimiteDePlan(Usuario organizador) {
        long vigentes = eventoRepository.contarVigentesDeOrganizador(
                organizador.getIdUsuario(), EstadoEventoOrganizadorNombre.DADO_DE_BAJA.name());

        if (vigentes >= limitePlanGratuito) {
            throw new OperacionNoPermitidaException(
                    "Alcanzaste el limite de " + limitePlanGratuito
                    + " eventos activos de tu plan. Da de baja alguno o mejora tu suscripcion.");
        }
    }

    // =====================================================================
    // DASHBOARD  (RF-2.8)
    // =====================================================================

    /**
     * Eventos del organizador, paginados y filtrables.
     *
     * TRES CONSULTAS POR PAGINA, NO TRES POR FILA: una para los eventos (con las
     * asociaciones *-a-uno resueltas por @EntityGraph), una para los detalles con
     * su ubicacion y una para las proximas fechas. Resolver ciudad y fecha
     * evento por evento seria el N+1 clasico: con 20 tarjetas, 41 consultas.
     */
    @Transactional(readOnly = true)
    public Page<EventoResponse> listarDeOrganizador(String email, String texto, Long idCategoria,
                                                    String estadoOrganizador, Pageable paginado) {

        Usuario organizador = usuarioRepository.buscarActivoPorEmailConRoles(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro la cuenta"));

        Page<Evento> pagina = eventoRepository.buscarDeOrganizador(
                organizador.getIdUsuario(), normalizar(texto), idCategoria,
                normalizar(estadoOrganizador), paginado);

        return mapearPagina(pagina);
    }

    /**
     * Convierte una pagina de entidades en una de DTOs, resolviendo ubicacion y
     * proxima fecha en bloque.
     *
     * Es publico porque el catalogo publico usa exactamente la misma mecanica:
     * duplicarla seria repetir la optimizacion (y el riesgo de olvidarla en uno
     * de los dos lados).
     */
    @Transactional(readOnly = true)
    public Page<EventoResponse> mapearPagina(Page<Evento> pagina) {
        List<Long> ids = pagina.getContent().stream().map(Evento::getIdEvento).toList();

        if (ids.isEmpty()) {
            return pagina.map(evento -> eventoMapper.aTarjeta(evento, null, null));
        }

        Map<Long, EventoDetalle> detalles = new HashMap<>();
        for (EventoDetalle detalle : eventoDetalleRepository.buscarConUbicacionPorIds(ids)) {
            detalles.put(detalle.getIdEvento(), detalle);
        }

        Map<Long, LocalDate> proximas = new HashMap<>();
        for (Object[] fila : cronogramaRepository.buscarProximaFechaPorEvento(ids, LocalDate.now())) {
            proximas.put((Long) fila[0], (LocalDate) fila[1]);
        }

        return pagina.map(evento -> eventoMapper.aTarjeta(
                evento,
                detalles.get(evento.getIdEvento()),
                proximas.get(evento.getIdEvento())));
    }

    // =====================================================================
    // BAJA LOGICA  (RF-2.9)
    // =====================================================================

    /**
     * Da de baja un evento propio. NUNCA borra la fila.
     *
     * RF-2.9 lo pide de forma explicita: se muta el estado a DADO_DE_BAJA y los
     * registros historicos quedan. Borrar de verdad rompeeria las inscripciones,
     * los pagos y las valoraciones que apuntan a este evento, y dejaria al
     * participante sin comprobante de algo que si ocurrio.
     *
     * PENDIENTE (Modulo 3): la rutina que invalida inscripciones y devuelve
     * pagos. Se anota aca en vez de simularla porque una devolucion a medias es
     * peor que una devolucion que todavia no existe.
     *
     * ISOLATION = READ_COMMITTED (obligatorio desde MariaDB 11.6)
     * MariaDB trae {@code innodb_snapshot_isolation=ON} por defecto: bajo
     * REPEATABLE READ, un SELECT ... FOR UPDATE que encuentra la fila modificada
     * DESPUES de la instantanea de la transaccion aborta con el error 1020
     * "Record has changed since last read". Es una proteccion deliberada contra
     * las escrituras sobre datos viejos, y es correcta: la transaccion iba a
     * decidir con informacion vencida.
     *
     * READ COMMITTED elimina el problema de raiz porque no mantiene instantanea:
     * cada sentencia ve lo ultimo confirmado, asi que el bloqueo simplemente
     * espera su turno y despues lee el estado real. Es ademas el nivel que usan
     * por defecto Oracle y PostgreSQL, y el adecuado para transacciones cortas
     * de escritura como esta.
     *
     * En MariaDB 10.4 esto no hacia falta: el servidor viejo dejaba pasar la
     * lectura con datos vencidos sin avisar, que es precisamente el bug.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EventoResponse darDeBaja(String email, Long idEvento, HttpServletRequest request) {

        Usuario organizador = usuarioRepository.buscarActivoPorEmailConRoles(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro la cuenta"));

        Evento evento = buscarPropio(idEvento, organizador);

        // Se relee con bloqueo de fila: el pipeline de moderacion puede estar
        // escribiendo este mismo evento ahora mismo. Sin esto, las dos
        // transacciones se pisan (ver EventoRepository.bloquearParaActualizar).
        // La verificacion de propiedad ya paso arriba, sobre la lectura previa.
        evento = eventoRepository.bloquearParaActualizar(idEvento)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro el evento"));

        String estadoActual = evento.getEstadoOrganizador() == null ? null
                : evento.getEstadoOrganizador().getEstadoOrganizador();

        if (EstadoEventoOrganizadorNombre.DADO_DE_BAJA.name().equalsIgnoreCase(estadoActual)) {
            throw new OperacionNoPermitidaException("El evento ya estaba dado de baja");
        }

        EventoEstadoOrganizador baja = estadoOrganizadorRepository
                .findByEstadoOrganizadorIgnoreCase(EstadoEventoOrganizadorNombre.DADO_DE_BAJA.name())
                .orElseThrow(() -> new ReglaNegocioException(
                        "El catalogo evento_estado_organizador no esta inicializado"));

        evento.setEstadoOrganizador(baja);
        eventoRepository.save(evento);

        registrarHistorial(evento, evento.getEstadoSistema(), baja, organizador);

        auditoriaService.registrar(organizador, AuditoriaService.ACCION_EVENTO_DADO_DE_BAJA,
                "Baja logica del evento " + idEvento, request);

        log.info("Evento {} dado de baja por el usuario {}", idEvento, organizador.getIdUsuario());

        return eventoMapper.aTarjeta(evento, null, null);
    }

    // =====================================================================
    // ESTADISTICAS  (RF-2.10)
    // =====================================================================

    @Transactional(readOnly = true)
    public EventoEstadisticasResponse estadisticas(String email, Long idEvento) {
        Usuario organizador = usuarioRepository.buscarActivoPorEmailConRoles(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro la cuenta"));

        Evento evento = buscarPropio(idEvento, organizador);

        return EventoEstadisticasResponse.builder()
                .idEvento(idEvento)
                .nombre(evento.getNombre())
                .visitasUnicas(visitaRepository.contarUnicasPorEvento(idEvento))
                .visitasTotales(visitaRepository.contarTotalesPorEvento(idEvento))
                .cantidadCronogramas(cronogramaRepository.countByEventoIdEvento(idEvento))
                .cupoTotalOfrecido(ticketRepository.sumarCupoMaximo(idEvento))
                .cupoOcupado(ticketRepository.sumarCupoActual(idEvento))
                .build();
    }

    // =====================================================================
    // Auxiliares
    // =====================================================================

    /**
     * Recupera un evento comprobando que sea del organizador.
     *
     * Lanza 404 tambien cuando el evento existe pero es de otro: responder 403
     * confirmaria que ese id esta ocupado y permitiria mapear la plataforma
     * iterando identificadores.
     */
    private Evento buscarPropio(Long idEvento, Usuario organizador) {
        Evento evento = eventoRepository.buscarConAsociaciones(idEvento)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro el evento"));

        boolean esPropio = evento.getOrganizador() != null
                && evento.getOrganizador().getIdUsuario().equals(organizador.getIdUsuario());

        if (!esPropio) {
            throw new RecursoNoEncontradoException("No se encontro el evento");
        }
        return evento;
    }

    private void registrarHistorial(Evento evento, EventoEstadoSistema estadoSistema,
                                    EventoEstadoOrganizador estadoOrganizador, Usuario usuario) {
        HistorialEstadoEvento historial = new HistorialEstadoEvento();
        historial.setEvento(evento);
        historial.setEstadoSistema(estadoSistema);
        historial.setEstadoOrganizador(estadoOrganizador);
        historial.setUsuario(usuario);
        historial.setFechaCambio(LocalDateTime.now());
        historialRepository.save(historial);
    }

    /**
     * Convierte "" en null.
     *
     * Las consultas usan el patron ":param IS NULL OR ...". Un filtro vacio que
     * llegara como cadena vacia NO seria null, asi que se aplicaria como
     * "LIKE '%%'" o, peor, como igualdad contra "" y no devolveria nada.
     */
    private String normalizar(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    /** Expuesto para que el catalogo publico reutilice la misma normalizacion. */
    public String normalizarFiltro(String valor) {
        return normalizar(valor);
    }
}
