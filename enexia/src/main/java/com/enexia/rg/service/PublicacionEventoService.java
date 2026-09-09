package com.enexia.rg.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.enexia.rg.dto.CronogramaRequest;
import com.enexia.rg.dto.EventoCrearRequest;
import com.enexia.rg.dto.TicketRequest;
import com.enexia.rg.dto.UbicacionRequest;
import com.enexia.rg.exception.RecursoNoEncontradoException;
import com.enexia.rg.exception.ReglaNegocioException;
import com.enexia.rg.model.Categoria;
import com.enexia.rg.model.Ciudad;
import com.enexia.rg.model.CronogramaTicket;
import com.enexia.rg.model.EstadoEventoSistemaNombre;
import com.enexia.rg.model.Evento;
import com.enexia.rg.model.EventoCronograma;
import com.enexia.rg.model.EventoDetalle;
import com.enexia.rg.model.EventoEstadoSistema;
import com.enexia.rg.model.EventoMultimedia;
import com.enexia.rg.model.HistorialEstadoEvento;
import com.enexia.rg.model.MotivoModeracionEvento;
import com.enexia.rg.model.TipoTicket;
import com.enexia.rg.model.Ubicacion;
import com.enexia.rg.repository.CategoriaRepository;
import com.enexia.rg.repository.CiudadRepository;
import com.enexia.rg.repository.CronogramaTicketRepository;
import com.enexia.rg.repository.EventoCronogramaRepository;
import com.enexia.rg.repository.EventoDetalleRepository;
import com.enexia.rg.repository.EventoEstadoSistemaRepository;
import com.enexia.rg.repository.EventoMultimediaRepository;
import com.enexia.rg.repository.EventoRepository;
import com.enexia.rg.repository.HistorialEstadoEventoRepository;
import com.enexia.rg.repository.TipoTicketRepository;
import com.enexia.rg.repository.UbicacionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Escrituras finales del pipeline de moderacion (RF-2.2, DFD 2.5B y 2.5C).
 *
 * POR QUE ES UNA CLASE APARTE DE ModeracionEventoService
 * El orquestador del pipeline es {@code @Async}: corre en un hilo del pool, sin
 * transaccion y sin peticion HTTP. Si sus metodos llevaran ademas
 * {@code @Transactional}, la transaccion abarcaria TAMBIEN las llamadas a
 * Cloudinary: una conexion a la base retenida varios segundos esperando una
 * respuesta de red, por cada imagen y por cada evento. Con el pool de conexiones
 * agotandose, el sitio entero se cae por culpa de un tercero lento.
 *
 * Separando, cada escritura abre su transaccion, hace lo suyo y la cierra;
 * mientras se habla con Cloudinary NO hay ninguna transaccion abierta.
 *
 * REQUIRES_NEW en ambos metodos: no hay transaccion previa que compartir (el
 * llamador es asincrono), y dejarlo explicito documenta que cada uno se confirma
 * por su cuenta.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PublicacionEventoService {

    private final EventoRepository eventoRepository;
    private final EventoDetalleRepository eventoDetalleRepository;
    private final EventoCronogramaRepository cronogramaRepository;
    private final CronogramaTicketRepository ticketRepository;
    private final EventoMultimediaRepository multimediaRepository;
    private final EventoEstadoSistemaRepository estadoSistemaRepository;
    private final HistorialEstadoEventoRepository historialRepository;
    private final CategoriaRepository categoriaRepository;
    private final CiudadRepository ciudadRepository;
    private final UbicacionRepository ubicacionRepository;
    private final TipoTicketRepository tipoTicketRepository;

    /**
     * Rama aprobada (DFD 2.5B): recien aca se persiste el CONTENIDO del evento.
     *
     * Hasta este momento en la base solo habia un "skeleton" con cuatro
     * metadatos (RF-2.2). El titulo, la descripcion, la ubicacion, la agenda,
     * los tickets y las imagenes entran todos juntos, en una unica transaccion,
     * despues de haber superado la moderacion. Esa es la regla arquitectonica
     * del proyecto: moderar antes de persistir contenido.
     *
     * @param urlsAprobadas URLs que devolvio Cloudinary, ya moderadas
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
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void aprobarYPersistir(Long idEvento, EventoCrearRequest datos, List<String> urlsAprobadas) {

        // Bloqueo de fila ANTES de tocar nada: el organizador puede estar dando
        // de baja este mismo evento en paralelo. Ver el javadoc del metodo.
        Evento evento = eventoRepository.bloquearParaActualizar(idEvento)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El evento " + idEvento + " ya no existe"));

        // --- Contenido de cabecera.
        evento.setNombre(datos.getNombre().trim());
        evento.setCategoria(buscarCategoria(datos.getIdCategoria()));

        // La portada es la primera imagen aprobada. Se guarda desnormalizada en
        // 'evento' (el MER ya preve url_portada) para que la grilla del catalogo
        // no tenga que consultar evento_multimedia por cada tarjeta.
        if (!urlsAprobadas.isEmpty()) {
            evento.setUrlPortada(urlsAprobadas.get(0));
        }

        // --- Detalle y ubicacion (RF-4.4).
        EventoDetalle detalle = new EventoDetalle();
        detalle.setEvento(evento);
        detalle.setDescripcion(datos.getDescripcion().trim());
        detalle.setUbicacion(persistirUbicacion(datos.getUbicacion()));
        eventoDetalleRepository.save(detalle);

        // --- Agenda y tickets (RF-2.4, RF-2.5, RF-2.6).
        persistirAgenda(evento, datos.getCronogramas());

        // --- Multimedia aprobada (RF-2.3).
        int orden = 1;
        for (String url : urlsAprobadas) {
            EventoMultimedia imagen = new EventoMultimedia();
            imagen.setEvento(evento);
            imagen.setTipoArchivo("IMAGEN");
            imagen.setUrlArchivo(url);
            imagen.setOrden(orden++);
            imagen.setFechaSubida(LocalDateTime.now());
            multimediaRepository.save(imagen);
        }

        // --- Estado final.
        cambiarEstado(evento, EstadoEventoSistemaNombre.APROBADO_SISTEMA, null);

        log.info("Evento {} APROBADO y publicado con {} imagen(es)", idEvento, urlsAprobadas.size());
    }

    /**
     * Rama rechazada (DFD 2.5C).
     *
     * NO se persiste nada del contenido: ni titulo, ni descripcion, ni
     * ubicacion, ni agenda, ni imagenes. RF-2.2 lo pide de forma explicita y la
     * razon es sencilla: guardar el texto que se acaba de rechazar por ofensivo
     * lo mete igual en la base, donde puede filtrarse por un listado, un backup o
     * un panel mal filtrado. Lo unico que queda es el skeleton con su estado y el
     * motivo, que es lo que el panel de administracion necesita para revisarlo.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void rechazar(Long idEvento, MotivoModeracionEvento motivo) {

        Evento evento = eventoRepository.bloquearParaActualizar(idEvento)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El evento " + idEvento + " ya no existe"));

        cambiarEstado(evento, EstadoEventoSistemaNombre.RECHAZADO_SISTEMA, motivo);

        log.warn("Evento {} RECHAZADO por {}", idEvento, motivo);
    }

    // =====================================================================
    // Auxiliares
    // =====================================================================

    /**
     * Cambia el estado de sistema y lo asienta en el historial.
     *
     * El MER exige trazabilidad de cada cambio (tabla historial_estado_evento).
     * Sin ella seria imposible responder "¿este evento estuvo publicado alguna
     * vez?" o "¿cuando lo rechazo el sistema?", que es exactamente lo que hace
     * falta cuando un organizador reclama.
     */
    private void cambiarEstado(Evento evento, EstadoEventoSistemaNombre estado,
                               MotivoModeracionEvento motivo) {

        EventoEstadoSistema fila = estadoSistemaRepository
                .buscarPorEstadoYMotivo(estado.name(), motivo == null ? null : motivo.name())
                .orElseThrow(() -> new ReglaNegocioException(
                        "El catalogo evento_estado_sistema no tiene la fila " + estado
                        + (motivo == null ? "" : "/" + motivo)));

        evento.setEstadoSistema(fila);
        eventoRepository.save(evento);

        HistorialEstadoEvento historial = new HistorialEstadoEvento();
        historial.setEvento(evento);
        historial.setEstadoSistema(fila);
        // Aca ya se puede confiar en la instancia: quien llama tomo el bloqueo de
        // fila, asi que si el organizador dio de baja el evento en paralelo, esa
        // baja ya esta confirmada y reflejada en lo que se leyo.
        historial.setEstadoOrganizador(evento.getEstadoOrganizador());
        // usuario null = lo cambio el sistema, no una persona. Cuando el cambio
        // venga del panel de admin (RF-6.1), ahi se registra quien lo hizo.
        historial.setUsuario(null);
        historial.setFechaCambio(LocalDateTime.now());
        historialRepository.save(historial);
    }

    private Categoria buscarCategoria(Long idCategoria) {
        return categoriaRepository.findById(idCategoria)
                .orElseThrow(() -> new ReglaNegocioException(
                        "La categoria indicada no existe en el catalogo"));
    }

    private Ubicacion persistirUbicacion(UbicacionRequest peticion) {
        Ciudad ciudad = ciudadRepository.findById(peticion.getIdCiudad())
                .orElseThrow(() -> new ReglaNegocioException(
                        "La ciudad indicada no existe en el catalogo"));

        Ubicacion ubicacion = new Ubicacion();
        ubicacion.setCalle(peticion.getCalle().trim());
        ubicacion.setNumeroExterior(peticion.getNumeroExterior().trim());
        ubicacion.setNumeroInterior(peticion.getNumeroInterior() == null
                ? null
                : peticion.getNumeroInterior().trim());
        ubicacion.setCiudad(ciudad);
        ubicacion.setLatitud(peticion.getLatitud());
        ubicacion.setLongitud(peticion.getLongitud());

        return ubicacionRepository.save(ubicacion);
    }

    private void persistirAgenda(Evento evento, List<CronogramaRequest> cronogramas) {
        for (CronogramaRequest peticion : cronogramas) {

            EventoCronograma cronograma = new EventoCronograma();
            cronograma.setEvento(evento);
            cronograma.setFecha(peticion.getFecha());
            cronograma.setHoraInicio(peticion.getHoraInicio());
            cronograma.setHoraFin(peticion.getHoraFin());
            cronograma = cronogramaRepository.save(cronograma);

            for (TicketRequest ticketPeticion : peticion.getTickets()) {
                CronogramaTicket ticket = new CronogramaTicket();
                ticket.setCronograma(cronograma);
                ticket.setTipoTicket(resolverTipoTicket(ticketPeticion.getTipoTicket()));
                ticket.setPrecio(ticketPeticion.getPrecio());
                ticket.setCupoMaximo(ticketPeticion.getCupoMaximo());
                // RF-2.6: el cupo ocupado arranca en cero y lo mueven las
                // inscripciones (Modulo 3). Dejarlo null haria fallar cualquier
                // resta posterior con NullPointerException.
                ticket.setCupoActual(0);
                ticketRepository.save(ticket);
            }
        }
    }

    /**
     * Busca el tipo de ticket en el catalogo y lo crea si no existe.
     *
     * El MER modela Tipo_Ticket como catalogo compartido. Obligar a un ABM previo
     * frenaria al organizador que necesita un tipo nuevo ("Pase de 3 dias") justo
     * cuando esta cargando el evento. Crearlo al vuelo mantiene el catalogo
     * normalizado -- los filtros por tipo siguen funcionando -- sin ese bloqueo.
     */
    private TipoTicket resolverTipoTicket(String nombre) {
        String limpio = nombre.trim();
        return tipoTicketRepository.findByNombreIgnoreCase(limpio)
                .orElseGet(() -> {
                    TipoTicket nuevo = new TipoTicket();
                    nuevo.setNombre(limpio);
                    return tipoTicketRepository.save(nuevo);
                });
    }
}
