package com.enexia.rg.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.enexia.rg.dto.EventoCronogramaResponse;
import com.enexia.rg.dto.EventoDetalleResponse;
import com.enexia.rg.dto.EventoResponse;
import com.enexia.rg.dto.TicketResponse;
import com.enexia.rg.model.Ciudad;
import com.enexia.rg.model.CronogramaTicket;
import com.enexia.rg.model.Evento;
import com.enexia.rg.model.EventoCronograma;
import com.enexia.rg.model.EventoDetalle;
import com.enexia.rg.model.EventoMultimedia;
import com.enexia.rg.model.PersonaFisica;
import com.enexia.rg.model.PersonaJuridica;
import com.enexia.rg.model.Ubicacion;

/**
 * Traduce entidades de evento a DTOs.
 *
 * POR QUE UNA CLASE PROPIA Y NO METODOS EN CADA SERVICE
 * El mismo evento se muestra en tres lugares con la misma forma: catalogo
 * publico (RF-4.1), dashboard del organizador (RF-2.8) y ficha tecnica (RF-4.4).
 * Duplicar el armado en cada service garantiza que tarde o temprano diverjan y
 * que la firma del organizador salga distinta en una pantalla que en otra.
 *
 * IMPORTANTE: estos metodos tocan asociaciones LAZY, asi que deben invocarse
 * DENTRO de una transaccion. Con {@code open-in-view=false}, llamarlos desde un
 * controller lanzaria LazyInitializationException.
 */
@Component
public class EventoMapper {

    /**
     * Firma publica del organizador (RF-7.4).
     *
     * ORDEN DE PRECEDENCIA, tal cual lo fija el requisito:
     *   1. Nombre de fantasia de la organizacion, si el evento va a nombre de una
     *      y ese campo opcional esta cargado.
     *   2. Razon social, si hay organizacion pero no hay fantasia.
     *   3. "Nombre Apellido" de la persona fisica, si el evento va a titulo personal.
     *
     * Se resuelve en el SERVIDOR y no en el frontend por dos razones: la regla es
     * una sola y debe verse igual en la grilla, la ficha y el dashboard; y
     * resolverla en el cliente obligaria a enviarle los datos de la persona
     * fisica (nombre y apellido civiles) incluso cuando el evento se publica a
     * nombre corporativo, exponiendo un dato personal que la vista no necesita.
     */
    public String firmaOrganizador(Evento evento) {
        PersonaJuridica organizacion = evento.getPersonaJuridica();

        if (organizacion != null) {
            String fantasia = organizacion.getNombreFantasia();
            if (fantasia != null && !fantasia.isBlank()) {
                return fantasia;
            }
            return organizacion.getRazonSocial();
        }

        if (evento.getOrganizador() == null) {
            return "Organizador no disponible";
        }

        PersonaFisica persona = evento.getOrganizador().getPersonaFisica();
        if (persona == null) {
            // Fallback razonable: el nickname es publico y siempre existe.
            return evento.getOrganizador().getNickname();
        }
        return (persona.getNombre() + " " + persona.getApellido()).trim();
    }

    /** Tarjeta de grilla (RF-4.1, RF-2.8). */
    public EventoResponse aTarjeta(Evento evento, EventoDetalle detalle, LocalDate proximaFecha) {
        Ciudad ciudad = ciudadDe(detalle);

        return EventoResponse.builder()
                .idEvento(evento.getIdEvento())
                // El nombre es null mientras el evento esta EN_PROCESO: RF-2.2
                // manda no persistir contenido hasta aprobarlo. Se muestra un
                // texto neutro para que la tarjeta del dashboard no quede vacia.
                .nombre(evento.getNombre() != null ? evento.getNombre() : "(Evento en validacion)")
                .urlPortada(evento.getUrlPortada())
                .categoria(evento.getCategoria() == null ? null : evento.getCategoria().getNombreCategoria())
                .organizador(firmaOrganizador(evento))
                .estadoSistema(evento.getEstadoSistema() == null ? null
                        : evento.getEstadoSistema().getEstadoSistema())
                .motivoRechazo(evento.getEstadoSistema() == null ? null
                        : evento.getEstadoSistema().getMotivoCodigo())
                .estadoOrganizador(evento.getEstadoOrganizador() == null ? null
                        : evento.getEstadoOrganizador().getEstadoOrganizador())
                .proximaFecha(proximaFecha == null ? null : proximaFecha.toString())
                .ciudad(ciudad == null ? null : ciudad.getNombre())
                .provincia(ciudad == null || ciudad.getProvincia() == null
                        ? null : ciudad.getProvincia().getNombre())
                .fechaCreacion(evento.getFechaCreacion())
                .build();
    }

    /**
     * Ficha tecnica completa (RF-4.4).
     *
     * Los tickets llegan en una lista plana de todos los cronogramas y se
     * agrupan aca por id de cronograma. La alternativa -- una consulta de tickets
     * por cada fecha -- seria el N+1 de siempre: un evento con diez funciones
     * dispararia once consultas para armar una sola pantalla.
     */
    public EventoDetalleResponse aFicha(Evento evento,
                                        EventoDetalle detalle,
                                        List<EventoCronograma> cronogramas,
                                        List<CronogramaTicket> tickets,
                                        List<EventoMultimedia> imagenes) {
        return aFicha(evento, detalle, cronogramas, tickets, imagenes,
                EstadoParticipante.vacio(), null, 0);
    }

    /**
     * Cuanto sabe el backend del visitante autenticado, para las tres marcas
     * "es prevesible que esto falle, no lo ofrezcas" de la ficha (RF-3.1,
     * RF-3.4): inscripto activo (boton "Inscribirme"), confirmado (piso para
     * poder valorar) y ya valorado (techo para no ofrecerlo de nuevo).
     *
     * Un record con los tres sets en vez de tres parametros sueltos: son datos
     * que siempre viajan juntos (los tres se calculan en el mismo momento, a
     * partir del mismo usuario y la misma lista de cronogramas) y crecer a un
     * cuarto o quinto set no deberia obligar a tocar la firma de aFicha otra
     * vez.
     */
    public record EstadoParticipante(
            Set<Long> idsInscriptoActivo,
            Set<Long> idsConfirmado,
            Set<Long> idsYaValorado) {

        public static EstadoParticipante vacio() {
            return new EstadoParticipante(Set.of(), Set.of(), Set.of());
        }
    }

    /**
     * Ficha tecnica completa, con el estado de participacion del visitante
     * (RF-3.1, RF-3.4) y las estadisticas de valoracion del evento.
     *
     * @param estadoParticipante ver {@link EstadoParticipante}; vacio para un
     *                           visitante anonimo o para el propio organizador
     *                           editando su evento
     * @param promedioValoracion null si el evento todavia no tiene ninguna
     *                           valoracion (distinto de "0 estrellas")
     * @param cantidadValoraciones total de valoraciones del evento, para el
     *                             "N opiniones" del header
     */
    public EventoDetalleResponse aFicha(Evento evento,
                                        EventoDetalle detalle,
                                        List<EventoCronograma> cronogramas,
                                        List<CronogramaTicket> tickets,
                                        List<EventoMultimedia> imagenes,
                                        EstadoParticipante estadoParticipante,
                                        Double promedioValoracion,
                                        long cantidadValoraciones) {

        Map<Long, List<CronogramaTicket>> ticketsPorCronograma = tickets.stream()
                .collect(Collectors.groupingBy(t -> t.getCronograma().getIdCronograma()));

        LocalDateTime ahora = LocalDateTime.now();

        List<EventoCronogramaResponse> agenda = new ArrayList<>();
        for (EventoCronograma cronograma : cronogramas) {
            boolean finalizado = LocalDateTime.of(cronograma.getFecha(), cronograma.getHoraFin())
                    .isBefore(ahora);

            boolean puedeValorar = finalizado
                    && estadoParticipante.idsConfirmado().contains(cronograma.getIdCronograma())
                    && !estadoParticipante.idsYaValorado().contains(cronograma.getIdCronograma());

            agenda.add(EventoCronogramaResponse.builder()
                    .idCronograma(cronograma.getIdCronograma())
                    .fecha(cronograma.getFecha())
                    .horaInicio(cronograma.getHoraInicio())
                    .horaFin(cronograma.getHoraFin())
                    .tickets(ticketsPorCronograma
                            .getOrDefault(cronograma.getIdCronograma(), List.of())
                            .stream()
                            .map(this::aTicket)
                            .toList())
                    .finalizado(finalizado)
                    .yaInscripto(estadoParticipante.idsInscriptoActivo().contains(cronograma.getIdCronograma()))
                    .puedeValorar(puedeValorar)
                    .build());
        }

        Ubicacion ubicacion = detalle == null ? null : detalle.getUbicacion();
        Ciudad ciudad = ciudadDe(detalle);

        return EventoDetalleResponse.builder()
                .idEvento(evento.getIdEvento())
                .nombre(evento.getNombre())
                .descripcion(detalle == null ? null : detalle.getDescripcion())
                .categoria(evento.getCategoria() == null ? null : evento.getCategoria().getNombreCategoria())
                .idCategoria(evento.getCategoria() == null ? null : evento.getCategoria().getIdCategoria())
                .urlPortada(evento.getUrlPortada())
                .organizador(firmaOrganizador(evento))
                .organizadorEsOrganizacion(evento.getPersonaJuridica() != null)
                .estadoSistema(evento.getEstadoSistema() == null ? null
                        : evento.getEstadoSistema().getEstadoSistema())
                .estadoOrganizador(evento.getEstadoOrganizador() == null ? null
                        : evento.getEstadoOrganizador().getEstadoOrganizador())
                .calle(ubicacion == null ? null : ubicacion.getCalle())
                .numero(ubicacion == null ? null : ubicacion.getNumeroExterior())
                .numeroInterior(ubicacion == null ? null : ubicacion.getNumeroInterior())
                .ciudad(ciudad == null ? null : ciudad.getNombre())
                .idCiudad(ciudad == null ? null : ciudad.getIdCiudad())
                .provincia(ciudad == null || ciudad.getProvincia() == null
                        ? null : ciudad.getProvincia().getNombre())
                .pais(ciudad == null || ciudad.getProvincia() == null
                        || ciudad.getProvincia().getPais() == null
                        ? null : ciudad.getProvincia().getPais().getNombre())
                .latitud(ubicacion == null ? null : ubicacion.getLatitud())
                .longitud(ubicacion == null ? null : ubicacion.getLongitud())
                .imagenes(imagenes.stream().map(EventoMultimedia::getUrlArchivo).toList())
                .cronogramas(agenda)
                .fechaCreacion(evento.getFechaCreacion())
                .promedioValoracion(promedioValoracion)
                .cantidadValoraciones(cantidadValoraciones)
                .build();
    }

    public TicketResponse aTicket(CronogramaTicket ticket) {
        int maximo = ticket.getCupoMaximo() == null ? 0 : ticket.getCupoMaximo();
        int actual = ticket.getCupoActual() == null ? 0 : ticket.getCupoActual();
        int disponible = Math.max(0, maximo - actual);

        BigDecimal precio = ticket.getPrecio() == null ? BigDecimal.ZERO : ticket.getPrecio();

        return TicketResponse.builder()
                .idCronogramaTicket(ticket.getIdCronogramaTicket())
                .tipoTicket(ticket.getTipoTicket() == null ? null : ticket.getTipoTicket().getNombre())
                .precio(precio)
                .cupoMaximo(maximo)
                .cupoActual(actual)
                .cupoDisponible(disponible)
                .agotado(disponible == 0)
                // compareTo y no equals: equals(BigDecimal) compara TAMBIEN la
                // escala, asi que 0.00 no seria "igual" a 0 y una entrada
                // gratuita cargada como 0.00 se mostraria como paga.
                .gratuito(precio.compareTo(BigDecimal.ZERO) == 0)
                .build();
    }

    private Ciudad ciudadDe(EventoDetalle detalle) {
        if (detalle == null || detalle.getUbicacion() == null) {
            return null;
        }
        return detalle.getUbicacion().getCiudad();
    }
}
