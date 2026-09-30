package com.enexia.rg.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.enexia.rg.dto.EventoCronogramaResponse;
import com.enexia.rg.dto.EventoDetalleResponse;
import com.enexia.rg.dto.TicketResponse;
import com.enexia.rg.model.CronogramaTicket;
import com.enexia.rg.model.Evento;
import com.enexia.rg.model.EventoCronograma;
import com.enexia.rg.model.PersonaFisica;
import com.enexia.rg.model.PersonaJuridica;
import com.enexia.rg.model.TipoTicket;
import com.enexia.rg.model.Usuario;

/**
 * Pruebas de {@link EventoMapper}: resolucion de la firma del organizador
 * (RF-7.4) y calculo de disponibilidad de tickets (RF-2.6).
 *
 * Es logica pura, sin dependencias: no hace falta ni Mockito.
 *
 * POR QUE MERECE PRUEBAS PROPIAS
 * La firma del organizador tiene TRES niveles de precedencia y un fallback. Es
 * exactamente el tipo de cadena de condiciones que se rompe al tocarla y donde
 * el error no se nota hasta que un evento corporativo aparece firmado con el
 * nombre y apellido de un empleado, que es una filtracion de dato personal.
 */
@DisplayName("EventoMapper - firma del organizador (RF-7.4) y cupos (RF-2.6)")
class EventoMapperTest {

    private final EventoMapper mapper = new EventoMapper();

    @Nested
    @DisplayName("Firma del organizador, por orden de precedencia")
    class Firma {

        @Test
        @DisplayName("1. Con organizacion y nombre de fantasia -> gana la fantasia")
        void prefiereNombreFantasia() {
            Evento evento = eventoConOrganizacion("Cultural Fueguina SRL", "Fueguina Eventos");

            assertThat(mapper.firmaOrganizador(evento)).isEqualTo("Fueguina Eventos");
        }

        @Test
        @DisplayName("2. Con organizacion sin fantasia -> razon social")
        void caeEnRazonSocial() {
            Evento evento = eventoConOrganizacion("Cultural Fueguina SRL", null);

            assertThat(mapper.firmaOrganizador(evento)).isEqualTo("Cultural Fueguina SRL");
        }

        @Test
        @DisplayName("2b. Fantasia vacia o en blanco cuenta como ausente")
        void fantasiaEnBlanco() {
            // Un campo opcional que el formulario mando como "" no puede
            // convertirse en la firma publica del evento.
            assertThat(mapper.firmaOrganizador(eventoConOrganizacion("Razon SRL", "")))
                    .isEqualTo("Razon SRL");
            assertThat(mapper.firmaOrganizador(eventoConOrganizacion("Razon SRL", "   ")))
                    .isEqualTo("Razon SRL");
        }

        @Test
        @DisplayName("3. Sin organizacion -> Nombre y Apellido civiles del organizador")
        void aTituloPersonal() {
            Evento evento = eventoATituloPersonal("Bruno", "Diaz");

            assertThat(mapper.firmaOrganizador(evento)).isEqualTo("Bruno Diaz");
        }

        @Test
        @DisplayName("Fallback: sin persona fisica cargada usa el nickname, no explota")
        void fallbackNickname() {
            Usuario usuario = new Usuario();
            usuario.setNickname("bruno_d");

            Evento evento = new Evento();
            evento.setOrganizador(usuario);

            // El mapper corre al pintar CADA tarjeta del catalogo: un NPE aca
            // tumbaria la pagina principal entera por un dato incompleto.
            assertThat(mapper.firmaOrganizador(evento)).isEqualTo("bruno_d");
        }

        @Test
        @DisplayName("Fallback extremo: sin organizador tampoco lanza")
        void fallbackSinOrganizador() {
            assertThat(mapper.firmaOrganizador(new Evento())).isEqualTo("Organizador no disponible");
        }
    }

    @Nested
    @DisplayName("Disponibilidad y precio de tickets")
    class Tickets {

        @Test
        @DisplayName("Calcula el cupo disponible y marca agotado en cero")
        void calculaDisponibilidad() {
            TicketResponse ticket = mapper.aTicket(ticket(BigDecimal.valueOf(1500), 100, 40));

            assertThat(ticket.getCupoDisponible()).isEqualTo(60);
            assertThat(ticket.isAgotado()).isFalse();

            TicketResponse agotado = mapper.aTicket(ticket(BigDecimal.valueOf(1500), 100, 100));
            assertThat(agotado.getCupoDisponible()).isZero();
            assertThat(agotado.isAgotado()).isTrue();
        }

        @Test
        @DisplayName("Nunca devuelve disponibilidad negativa")
        void nuncaNegativo() {
            // Si por una condicion de carrera el cupo actual superara el maximo,
            // mostrar "-3 lugares" seria peor que mostrar agotado.
            TicketResponse ticket = mapper.aTicket(ticket(BigDecimal.TEN, 10, 13));

            assertThat(ticket.getCupoDisponible()).isZero();
            assertThat(ticket.isAgotado()).isTrue();
        }

        @Test
        @DisplayName("Precio 0.00 se reconoce como gratuito, no solo el 0 exacto")
        void gratuitoConEscala() {
            // compareTo y no equals: equals(BigDecimal) compara TAMBIEN la
            // escala, asi que 0.00 no seria "igual" a 0 y una entrada gratuita
            // cargada con decimales se mostraria como paga.
            assertThat(mapper.aTicket(ticket(new BigDecimal("0.00"), 10, 0)).isGratuito()).isTrue();
            assertThat(mapper.aTicket(ticket(BigDecimal.ZERO, 10, 0)).isGratuito()).isTrue();
            assertThat(mapper.aTicket(ticket(new BigDecimal("0.01"), 10, 0)).isGratuito()).isFalse();
        }

        @Test
        @DisplayName("Precio o cupos nulos no rompen el mapeo")
        void toleraNulos() {
            CronogramaTicket sinDatos = new CronogramaTicket();
            sinDatos.setTipoTicket(tipoTicket());

            TicketResponse ticket = mapper.aTicket(sinDatos);

            assertThat(ticket.getPrecio()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(ticket.getCupoDisponible()).isZero();
        }
    }

    @Nested
    @DisplayName("Ficha tecnica: vigencia y estado de inscripcion por cronograma (RF-3.1, RF-4.4)")
    class Ficha {

        @Test
        @DisplayName("Cronograma cuya hora_fin ya paso -> finalizado = true")
        void marcaFinalizado() {
            Evento evento = eventoATituloPersonal("Bruno", "Diaz");
            EventoCronograma pasado = cronograma(1L, LocalDate.now().minusDays(1), LocalTime.of(20, 0));

            EventoDetalleResponse ficha = mapper.aFicha(evento, null, List.of(pasado), List.of(), List.of(),
                    EventoMapper.EstadoParticipante.vacio(), null, 0);

            assertThat(ficha.getCronogramas().get(0).isFinalizado()).isTrue();
        }

        @Test
        @DisplayName("Cronograma futuro -> finalizado = false")
        void marcaVigente() {
            Evento evento = eventoATituloPersonal("Bruno", "Diaz");
            EventoCronograma futuro = cronograma(1L, LocalDate.now().plusDays(1), LocalTime.of(20, 0));

            EventoDetalleResponse ficha = mapper.aFicha(evento, null, List.of(futuro), List.of(), List.of(),
                    EventoMapper.EstadoParticipante.vacio(), null, 0);

            assertThat(ficha.getCronogramas().get(0).isFinalizado()).isFalse();
        }

        @Test
        @DisplayName("yaInscripto solo se marca en los cronogramas del set recibido")
        void marcaYaInscripto() {
            Evento evento = eventoATituloPersonal("Bruno", "Diaz");
            EventoCronograma conInscripcion = cronograma(1L, LocalDate.now().plusDays(1), LocalTime.of(20, 0));
            EventoCronograma sinInscripcion = cronograma(2L, LocalDate.now().plusDays(2), LocalTime.of(20, 0));

            EventoDetalleResponse ficha = mapper.aFicha(
                    evento, null, List.of(conInscripcion, sinInscripcion), List.of(), List.of(),
                    new EventoMapper.EstadoParticipante(Set.of(1L), Set.of(), Set.of()), null, 0);

            List<EventoCronogramaResponse> agenda = ficha.getCronogramas();
            assertThat(agenda.get(0).isYaInscripto()).isTrue();
            assertThat(agenda.get(1).isYaInscripto()).isFalse();
        }

        @Test
        @DisplayName("El overload sin estado (edicion del organizador) nunca marca yaInscripto ni puedeValorar")
        void overloadSinEstadoNuncaMarca() {
            Evento evento = eventoATituloPersonal("Bruno", "Diaz");
            EventoCronograma futuro = cronograma(1L, LocalDate.now().plusDays(1), LocalTime.of(20, 0));

            EventoDetalleResponse ficha = mapper.aFicha(evento, null, List.of(futuro), List.of(), List.of());

            assertThat(ficha.getCronogramas().get(0).isYaInscripto()).isFalse();
            assertThat(ficha.getCronogramas().get(0).isPuedeValorar()).isFalse();
        }

        @Test
        @DisplayName("puedeValorar: finalizado + confirmado + no valorado aun -> true")
        void marcaPuedeValorar() {
            Evento evento = eventoATituloPersonal("Bruno", "Diaz");
            EventoCronograma finalizado = cronograma(1L, LocalDate.now().minusDays(1), LocalTime.of(20, 0));

            EventoDetalleResponse ficha = mapper.aFicha(evento, null, List.of(finalizado), List.of(), List.of(),
                    new EventoMapper.EstadoParticipante(Set.of(), Set.of(1L), Set.of()), null, 0);

            assertThat(ficha.getCronogramas().get(0).isPuedeValorar()).isTrue();
        }

        @Test
        @DisplayName("puedeValorar: todavia no finalizo -> false, aunque este confirmado")
        void noPuedeValorarSiNoFinalizo() {
            Evento evento = eventoATituloPersonal("Bruno", "Diaz");
            EventoCronograma futuro = cronograma(1L, LocalDate.now().plusDays(1), LocalTime.of(20, 0));

            EventoDetalleResponse ficha = mapper.aFicha(evento, null, List.of(futuro), List.of(), List.of(),
                    new EventoMapper.EstadoParticipante(Set.of(), Set.of(1L), Set.of()), null, 0);

            assertThat(ficha.getCronogramas().get(0).isPuedeValorar()).isFalse();
        }

        @Test
        @DisplayName("puedeValorar: finalizado pero sin inscripcion CONFIRMADA -> false")
        void noPuedeValorarSinConfirmar() {
            Evento evento = eventoATituloPersonal("Bruno", "Diaz");
            EventoCronograma finalizado = cronograma(1L, LocalDate.now().minusDays(1), LocalTime.of(20, 0));

            EventoDetalleResponse ficha = mapper.aFicha(evento, null, List.of(finalizado), List.of(), List.of(),
                    EventoMapper.EstadoParticipante.vacio(), null, 0);

            assertThat(ficha.getCronogramas().get(0).isPuedeValorar()).isFalse();
        }

        @Test
        @DisplayName("puedeValorar: finalizado, confirmado, pero ya valorado -> false")
        void noPuedeValorarSiYaValoro() {
            Evento evento = eventoATituloPersonal("Bruno", "Diaz");
            EventoCronograma finalizado = cronograma(1L, LocalDate.now().minusDays(1), LocalTime.of(20, 0));

            EventoDetalleResponse ficha = mapper.aFicha(evento, null, List.of(finalizado), List.of(), List.of(),
                    new EventoMapper.EstadoParticipante(Set.of(), Set.of(1L), Set.of(1L)), null, 0);

            assertThat(ficha.getCronogramas().get(0).isPuedeValorar()).isFalse();
        }

        @Test
        @DisplayName("Promedio y cantidad de valoraciones viajan tal cual a la ficha")
        void trasladaPromedioYCantidad() {
            Evento evento = eventoATituloPersonal("Bruno", "Diaz");

            EventoDetalleResponse ficha = mapper.aFicha(evento, null, List.of(), List.of(), List.of(),
                    EventoMapper.EstadoParticipante.vacio(), 4.5, 12);

            assertThat(ficha.getPromedioValoracion()).isEqualTo(4.5);
            assertThat(ficha.getCantidadValoraciones()).isEqualTo(12);
        }
    }

    // =====================================================================
    // Auxiliares
    // =====================================================================

    private EventoCronograma cronograma(Long idCronograma, LocalDate fecha, LocalTime horaFin) {
        EventoCronograma cronograma = new EventoCronograma();
        cronograma.setIdCronograma(idCronograma);
        cronograma.setFecha(fecha);
        cronograma.setHoraInicio(horaFin.minusHours(2));
        cronograma.setHoraFin(horaFin);
        return cronograma;
    }

    private Evento eventoConOrganizacion(String razonSocial, String nombreFantasia) {
        PersonaJuridica organizacion = new PersonaJuridica();
        organizacion.setRazonSocial(razonSocial);
        organizacion.setNombreFantasia(nombreFantasia);

        Evento evento = eventoATituloPersonal("Bruno", "Diaz");
        evento.setPersonaJuridica(organizacion);
        return evento;
    }

    private Evento eventoATituloPersonal(String nombre, String apellido) {
        PersonaFisica persona = new PersonaFisica();
        persona.setNombre(nombre);
        persona.setApellido(apellido);

        Usuario usuario = new Usuario();
        usuario.setNickname("bruno_d");
        usuario.setPersonaFisica(persona);

        Evento evento = new Evento();
        evento.setOrganizador(usuario);
        return evento;
    }

    private CronogramaTicket ticket(BigDecimal precio, int maximo, int actual) {
        CronogramaTicket ticket = new CronogramaTicket();
        ticket.setTipoTicket(tipoTicket());
        ticket.setPrecio(precio);
        ticket.setCupoMaximo(maximo);
        ticket.setCupoActual(actual);
        return ticket;
    }

    private TipoTicket tipoTicket() {
        TipoTicket tipo = new TipoTicket();
        tipo.setNombre("Inscripcion General");
        return tipo;
    }
}
