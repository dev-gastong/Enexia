package com.enexia.rg.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.enexia.rg.dto.TicketResponse;
import com.enexia.rg.model.CronogramaTicket;
import com.enexia.rg.model.Evento;
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

    // =====================================================================
    // Auxiliares
    // =====================================================================

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
