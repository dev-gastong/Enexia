package com.enexia.rg.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.enexia.rg.model.NotificacionTipo;

/**
 * El despachador (N.5 del DFD): idempotencia, envio despues del commit y
 * contencion de fallos.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EmailService - despacho de notificaciones")
class EmailServiceTest {

    @Mock private BrevoClient brevo;
    @Mock private NotificacionService notificaciones;

    /** Cuenta cuantas tareas se encolaron y las ejecuta en el acto. */
    private final AtomicInteger encoladas = new AtomicInteger();
    private EmailService servicio;

    private static final EmailService.Destinatario ANA =
            new EmailService.Destinatario(7L, "ana@enexia.test");

    @BeforeEach
    void preparar() {
        Executor inmediato = tarea -> {
            encoladas.incrementAndGet();
            tarea.run();
        };
        servicio = new EmailService(brevo, notificaciones, inmediato, "http://localhost:8080", 0);

        when(brevo.enviar(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        when(notificaciones.reservar(any(), anyLong(), any())).thenReturn(Optional.of(99L));
    }

    @Test
    @DisplayName("Un aviso sin libro (accion unica de una persona) se envia directo")
    void avisoSimple() {
        servicio.enviarPasswordCambiada("ana@enexia.test");

        verify(brevo).enviar(eq("ana@enexia.test"), anyString(), anyString(), anyString());
        verify(notificaciones, never()).reservar(any(), anyLong(), any());
    }

    @Test
    @DisplayName("Recordatorio: reserva en el libro, envia y asienta el resultado")
    void recordatorioReservaYAsienta() {
        servicio.enviarRecordatorio(ANA, 55L, "Recital", LocalDate.of(2026, 10, 20), LocalTime.of(21, 0));

        verify(notificaciones).reservar(NotificacionTipo.RECORDATORIO_10MIN, 7L, 55L);
        verify(brevo).enviar(eq("ana@enexia.test"), anyString(), anyString(), anyString());
        verify(notificaciones).registrarResultado(99L, true);
    }

    @Test
    @DisplayName("Si el aviso ya estaba reservado, NO se vuelve a enviar")
    void idempotencia() {
        when(notificaciones.reservar(any(), anyLong(), any())).thenReturn(Optional.empty());

        servicio.enviarRecordatorio(ANA, 55L, "Recital", LocalDate.of(2026, 10, 20), LocalTime.of(21, 0));

        // Con un tick por minuto la misma inscripcion cae en la ventana ~10
        // veces. Sin esto, el participante recibiria 10 correos.
        verify(brevo, never()).enviar(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Un duplicado concurrente (UNIQUE de la base) se descarta sin enviar")
    void duplicadoConcurrente() {
        when(notificaciones.reservar(any(), anyLong(), any()))
                .thenThrow(new DataIntegrityViolationException("uk_notificacion_tipo_usuario_ref"));

        servicio.enviarRecordatorio(ANA, 55L, "Recital", LocalDate.of(2026, 10, 20), LocalTime.of(21, 0));

        verify(brevo, never()).enviar(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Si el libro falla, se prefiere NO enviar antes que arriesgar un duplicado")
    void libroCaido() {
        when(notificaciones.reservar(any(), anyLong(), any())).thenThrow(new IllegalStateException("base caida"));

        servicio.enviarRecordatorio(ANA, 55L, "Recital", LocalDate.of(2026, 10, 20), LocalTime.of(21, 0));

        verify(brevo, never()).enviar(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Un fallo del proveedor queda asentado como FALLIDO y no propaga")
    void falloDelProveedor() {
        when(brevo.enviar(anyString(), anyString(), anyString(), anyString())).thenReturn(false);

        servicio.enviarInscripcionConfirmada(ANA, 3L, "Recital", LocalDate.of(2026, 10, 20),
                LocalTime.of(21, 0), "General");

        verify(notificaciones).registrarResultado(99L, false);
    }

    @Test
    @DisplayName("Una excepcion del cliente no escapa al llamador")
    void excepcionNoEscapa() {
        when(brevo.enviar(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("boom"));

        // Si escapara, un correo caido tumbaria el login o la creacion de un evento.
        servicio.enviarCuentaSuspendida("ana@enexia.test");
    }

    @Test
    @DisplayName("Envio masivo: un correo por inscripto, cada uno con su reserva")
    void envioMasivo() {
        List<EmailService.Destinatario> inscriptos = List.of(
                ANA, new EmailService.Destinatario(8L, "beto@enexia.test"));

        servicio.enviarEventoCancelado(inscriptos, 12L, "Recital");

        verify(notificaciones).reservar(NotificacionTipo.EVENTO_CANCELADO, 7L, 12L);
        verify(notificaciones).reservar(NotificacionTipo.EVENTO_CANCELADO, 8L, 12L);
        verify(brevo).enviar(eq("ana@enexia.test"), anyString(), anyString(), anyString());
        verify(brevo).enviar(eq("beto@enexia.test"), anyString(), anyString(), anyString());
        // Una sola tarea para todo el lote: asi la pausa entre correos es real.
        assertThat(encoladas.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Envio masivo sin destinatarios: no encola nada")
    void masivoVacio() {
        servicio.enviarEventoCancelado(List.of(), 12L, "Recital");

        assertThat(encoladas.get()).isZero();
    }

    @Test
    @DisplayName("Dentro de una transaccion, el envio espera al COMMIT")
    void esperaAlCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            servicio.enviarEventoReactivado("ana@enexia.test", "Recital");

            // Todavia no: la transaccion de negocio no cerro. Si se revirtiera,
            // el correo avisaria de algo que no ocurrio.
            verify(brevo, never()).enviar(anyString(), anyString(), anyString(), anyString());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(brevo).enviar(eq("ana@enexia.test"), anyString(), anyString(), anyString());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("Si la transaccion se revierte, no sale ningun correo")
    void noEnviaSiSeRevierte() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            servicio.enviarEventoSuspendido("ana@enexia.test", "Recital", "FRAUDE_DETECTADO", "texto largo");

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            verify(brevo, never()).enviar(anyString(), anyString(), anyString(), anyString());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("La suspension de evento lleva el motivo y la observacion del administrador")
    void contenidoDeLaSuspension() {
        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);

        servicio.enviarEventoSuspendido("ana@enexia.test", "Recital", "FRAUDE_DETECTADO",
                "Las entradas no existian");

        verify(brevo).enviar(eq("ana@enexia.test"), anyString(), texto.capture(), anyString());
        assertThat(texto.getValue()).contains("Recital").contains("irregularidad grave")
                .contains("Las entradas no existian");
    }

    @Test
    @DisplayName("La invitacion a valorar lleva el enlace a la ficha del evento")
    void invitacionConEnlace() {
        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);

        servicio.enviarInvitacionValorar(List.of(ANA), 12L, "Recital");

        verify(brevo).enviar(eq("ana@enexia.test"), anyString(), texto.capture(), html.capture());
        assertThat(texto.getValue()).contains("http://localhost:8080/evento-detalle.html?id=12");
        assertThat(html.getValue()).contains("<a href=\"http://localhost:8080/evento-detalle.html?id=12\"");
    }

    @Test
    @DisplayName("El nombre de un evento con HTML no se inyecta en el cuerpo")
    void nombreConHtml() {
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);

        servicio.enviarEventoReactivado("ana@enexia.test", "<script>alert(1)</script>");

        verify(brevo).enviar(eq("ana@enexia.test"), anyString(), anyString(), html.capture());
        assertThat(html.getValue()).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    @Test
    @DisplayName("El enlace de recuperacion apunta al frontend configurado, no a un host ajeno")
    void enlaceDeRecuperacion() {
        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);

        servicio.enviarRecuperacionPassword("ana@enexia.test", "TOKEN123");

        verify(brevo).enviar(eq("ana@enexia.test"), anyString(), texto.capture(), anyString());
        assertThat(texto.getValue()).contains("http://localhost:8080/pages/auth/recuperar.html?token=TOKEN123");
    }
}
