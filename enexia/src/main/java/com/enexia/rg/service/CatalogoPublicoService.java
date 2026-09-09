package com.enexia.rg.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.enexia.rg.dto.EventoDetalleResponse;
import com.enexia.rg.dto.EventoResponse;
import com.enexia.rg.exception.RecursoNoEncontradoException;
import com.enexia.rg.exception.ReglaNegocioException;
import com.enexia.rg.model.CronogramaTicket;
import com.enexia.rg.model.EstadoEventoOrganizadorNombre;
import com.enexia.rg.model.Evento;
import com.enexia.rg.model.EventoCronograma;
import com.enexia.rg.model.EventoDetalle;
import com.enexia.rg.repository.CronogramaTicketRepository;
import com.enexia.rg.repository.EventoCronogramaRepository;
import com.enexia.rg.repository.EventoDetalleRepository;
import com.enexia.rg.repository.EventoMultimediaRepository;
import com.enexia.rg.repository.EventoRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Interfaz publica: catalogo, busqueda, filtros y ficha tecnica
 * (Modulo 4 - RF-4.1 a RF-4.5; DFD 4.1/4.2/4.3 y 4.4/4.5).
 *
 * SESION OPCIONAL, NO OBLIGATORIA
 * Es la diferencia de fondo con el resto de la API. Aca el JWT puede venir o no:
 * si viene, se aprovecha para atribuir la visita a un usuario (RF-4.5); si no
 * viene, la consulta funciona igual. Nunca bloquea. Por eso los metodos reciben
 * el email como parametro que admite null en vez de exigirlo.
 *
 * VISIBILIDAD: DOS CONDICIONES QUE SE CUMPLEN JUNTAS
 * Un evento aparece en el catalogo solo si (a) la moderacion lo aprobo
 * (APROBADO_SISTEMA o APROBADO_MANUAL) y (b) su organizador lo mantiene
 * PUBLICADO. Comprobar una sola dejaria entrar eventos cancelados o todavia en
 * revision. La regla vive en las consultas del repositorio, no en el service, y
 * por eso no se puede saltear pidiendo un id directo.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogoPublicoService {

    private final EventoRepository eventoRepository;
    private final EventoDetalleRepository eventoDetalleRepository;
    private final EventoCronogramaRepository cronogramaRepository;
    private final CronogramaTicketRepository ticketRepository;
    private final EventoMultimediaRepository multimediaRepository;
    private final EventoService eventoService;
    private final VisitaService visitaService;
    private final EventoMapper eventoMapper;

    // =====================================================================
    // CATALOGO  (RF-4.1, RF-4.2, RF-4.3)
    // =====================================================================

    /**
     * Grilla paginada con busqueda de texto y filtros combinables.
     *
     * Todos los filtros son opcionales e independientes: se pueden usar solos o
     * combinados, y la consulta los ignora cuando llegan nulos.
     *
     * @param texto       coincidencia parcial sobre el nombre (RF-4.2)
     * @param idCategoria filtro por categoria (RF-4.3)
     * @param idProvincia filtro geografico de primer nivel (RF-4.3)
     * @param idCiudad    filtro geografico de segundo nivel (RF-4.3)
     * @param desde       primera fecha del rango, inclusive
     * @param hasta       ultima fecha del rango, inclusive
     */
    @Transactional(readOnly = true)
    public Page<EventoResponse> buscar(String texto, Long idCategoria, Long idProvincia,
                                       Long idCiudad, LocalDate desde, LocalDate hasta,
                                       Pageable paginado) {

        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new ReglaNegocioException(
                    "El rango de fechas es invalido: 'hasta' es anterior a 'desde'");
        }

        Page<Evento> pagina = eventoRepository.buscarCatalogoPublico(
                EventoRepository.ESTADOS_VISIBLES,
                EstadoEventoOrganizadorNombre.PUBLICADO.name(),
                eventoService.normalizarFiltro(texto),
                idCategoria,
                idProvincia,
                idCiudad,
                desde,
                hasta,
                paginado);

        // Se reutiliza el mapeo del dashboard: misma tarjeta, mismas tres
        // consultas por pagina, misma resolucion de firma del organizador.
        return eventoService.mapearPagina(pagina);
    }

    // =====================================================================
    // FICHA TECNICA  (RF-4.4) + VISITA (RF-4.5)
    // =====================================================================

    /**
     * Ficha completa de un evento y registro de la visita.
     *
     * @param emailUsuario email del visitante si hay sesion; null si es anonimo
     */
    @Transactional(readOnly = true)
    public EventoDetalleResponse verFicha(Long idEvento, String emailUsuario) {

        // --- Paso 4.4.1: existencia Y visibilidad en una sola consulta.
        Evento evento = eventoRepository.buscarPublicoPorId(
                        idEvento,
                        EventoRepository.ESTADOS_VISIBLES,
                        EstadoEventoOrganizadorNombre.PUBLICADO.name())
                // 404 unico para "no existe" y para "existe pero no es publico":
                // distinguirlos permitiria descubrir que eventos fueron
                // rechazados por moderacion probando ids.
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Evento no encontrado o no disponible"));

        // --- Paso 4.4.2: componer la ficha.
        EventoDetalle detalle = eventoDetalleRepository.buscarConUbicacion(idEvento).orElse(null);

        List<EventoCronograma> cronogramas =
                cronogramaRepository.findByEventoIdEventoOrderByFechaAscHoraInicioAsc(idEvento);

        List<CronogramaTicket> tickets = cronogramas.isEmpty()
                ? List.of()
                : ticketRepository.buscarPorCronogramas(
                        cronogramas.stream().map(EventoCronograma::getIdCronograma).toList());

        EventoDetalleResponse ficha = eventoMapper.aFicha(
                evento, detalle, cronogramas, tickets,
                multimediaRepository.findByEventoIdEventoOrderByOrdenAsc(idEvento));

        // --- Paso 4.5: registro pasivo de la visita.
        //
        // Va a OTRO BEAN a proposito. Este metodo es readOnly, y un INSERT
        // dentro de una transaccion de solo lectura la marca rollback-only:
        // la ficha entera respondia 500 por culpa de una metrica. El
        // REQUIRES_NEW de VisitaService solo se aplica si la llamada cruza el
        // proxy de Spring, o sea si sale de esta clase. Ver ese javadoc.
        visitaService.registrar(idEvento, emailUsuario);

        return ficha;
    }

}
