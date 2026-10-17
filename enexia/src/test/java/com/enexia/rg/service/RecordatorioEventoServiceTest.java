package com.enexia.rg.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.enexia.rg.model.CronogramaTicket;
import com.enexia.rg.model.Evento;
import com.enexia.rg.model.EventoCronograma;
import com.enexia.rg.model.Inscripcion;
import com.enexia.rg.model.Usuario;
import com.enexia.rg.repository.InscripcionRepository;

/**
 * La ventana del recordatorio "empieza en 10 minutos". El reloj entra como
 * parametro, asi que se prueba sin esperar.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RecordatorioEventoService - ventana de aviso previo")
class RecordatorioEventoServiceTest {

    @Mock private InscripcionRepository inscripcionRepository;
    @Mock private EmailService emailService;

    private RecordatorioEventoService servicio;

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 20, 20, 50, 0);

    @BeforeEach
    void preparar() {
        servicio = new RecordatorioEventoService(inscripcionRepository, emailService, 600);
    }

    @Test
    @DisplayName("Empieza dentro de la ventana: se avisa")
    void dentroDeLaVentana() {
        hayCandidata(LocalDate.of(2026, 10, 20), LocalTime.of(20, 58));

        assertThat(servicio.avisarProximos(AHORA)).isEqualTo(1);

        verify(emailService).enviarRecordatorio(any(), eq(55L), eq("Recital"),
                eq(LocalDate.of(2026, 10, 20)), eq(LocalTime.of(20, 58)));
    }

    @Test
    @DisplayName("Empieza justo a los 10 minutos: se avisa (el limite es inclusivo)")
    void limiteInclusivo() {
        hayCandidata(LocalDate.of(2026, 10, 20), LocalTime.of(21, 0));

        assertThat(servicio.avisarProximos(AHORA)).isEqualTo(1);
    }

    @Test
    @DisplayName("Falta mas de 10 minutos: todavia no se avisa")
    void todaviaFalta() {
        hayCandidata(LocalDate.of(2026, 10, 20), LocalTime.of(21, 1));

        assertThat(servicio.avisarProximos(AHORA)).isZero();
        verify(emailService, never()).enviarRecordatorio(any(), any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("Ya empezo: no se avisa de algo que paso")
    void yaEmpezo() {
        hayCandidata(LocalDate.of(2026, 10, 20), LocalTime.of(20, 49));

        assertThat(servicio.avisarProximos(AHORA)).isZero();
    }

    @Test
    @DisplayName("La ventana que cruza la medianoche consulta ambas fechas y avisa a la del dia siguiente")
    void cruzaLaMedianoche() {
        LocalDateTime casiMedianoche = LocalDateTime.of(2026, 10, 20, 23, 55);
        hayCandidata(LocalDate.of(2026, 10, 21), LocalTime.of(0, 3));

        assertThat(servicio.avisarProximos(casiMedianoche)).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LocalDate>> fechas = ArgumentCaptor.forClass(List.class);
        verify(inscripcionRepository).candidatasARecordatorio(fechas.capture(), anyString(), anyList(), anyString());
        // Si solo se pidiera "hoy", un evento a las 00:03 nunca recibiria aviso.
        assertThat(fechas.getValue())
                .containsExactly(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21));
    }

    @Test
    @DisplayName("Dentro del mismo dia se consulta una sola fecha")
    void unaSolaFecha() {
        servicio.avisarProximos(AHORA);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LocalDate>> fechas = ArgumentCaptor.forClass(List.class);
        verify(inscripcionRepository).candidatasARecordatorio(fechas.capture(), anyString(), anyList(), anyString());
        assertThat(fechas.getValue()).containsExactly(LocalDate.of(2026, 10, 20));
    }

    @Test
    @DisplayName("Funcion sin hora de inicio: se ignora sin romper el barrido")
    void sinHora() {
        hayCandidata(LocalDate.of(2026, 10, 20), null);

        assertThat(servicio.avisarProximos(AHORA)).isZero();
    }

    @Test
    @DisplayName("Adelanto de 30 segundos (para probar): avisa a los 20 s y no a los 40 s")
    void adelantoCorto() {
        RecordatorioEventoService rapido = new RecordatorioEventoService(inscripcionRepository, emailService, 30);

        hayCandidata(LocalDate.of(2026, 10, 20), LocalTime.of(20, 50, 20));
        assertThat(rapido.avisarProximos(AHORA)).isEqualTo(1);

        hayCandidata(LocalDate.of(2026, 10, 20), LocalTime.of(20, 50, 40));
        assertThat(rapido.avisarProximos(AHORA)).isZero();
    }

    @Test
    @DisplayName("Sin candidatas: no hay envios")
    void sinCandidatas() {
        when(inscripcionRepository.candidatasARecordatorio(anyList(), anyString(), anyList(), anyString()))
                .thenReturn(List.of());

        assertThat(servicio.avisarProximos(AHORA)).isZero();
    }

    private void hayCandidata(LocalDate fecha, LocalTime hora) {
        Evento evento = new Evento();
        evento.setNombre("Recital");

        EventoCronograma funcion = new EventoCronograma();
        funcion.setEvento(evento);
        funcion.setFecha(fecha);
        funcion.setHoraInicio(hora);

        CronogramaTicket ticket = new CronogramaTicket();
        ticket.setCronograma(funcion);

        Usuario participante = new Usuario();
        participante.setIdUsuario(7L);
        participante.setEmail("ana@enexia.test");

        Inscripcion inscripcion = new Inscripcion();
        inscripcion.setIdInscripcion(55L);
        inscripcion.setCronogramaTicket(ticket);
        inscripcion.setUsuario(participante);

        when(inscripcionRepository.candidatasARecordatorio(anyList(), anyString(), anyList(), anyString()))
                .thenReturn(List.of(inscripcion));
    }
}
