package com.enexia.rg.service;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Dispara cada minuto {@link RecordatorioEventoService}.
 *
 * Vive en su propio bean por la misma razon que
 * {@code FinalizacionEventoScheduler}: la transaccion del service tiene que
 * abrirse a traves del proxy.
 *
 * Se apaga con {@code enexia.notificaciones.recordatorio.habilitado=false}
 * (por ejemplo en una prueba que no quiere correos salidos del reloj).
 *
 * LA ZONA HORARIA IMPORTA
 * Las funciones se guardan como fecha y hora locales sin zona. Un servidor en
 * UTC (Render) con {@code LocalDateTime.now()} estaria 3 horas adelantado y
 * mandaria los recordatorios a destiempo, por eso se pide la hora explicitamente
 * de la zona del evento.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "enexia.notificaciones.recordatorio.habilitado",
        havingValue = "true", matchIfMissing = true)
public class RecordatorioEventoScheduler {

    private final RecordatorioEventoService servicio;
    private final ZoneId zona;

    public RecordatorioEventoScheduler(
            RecordatorioEventoService servicio,
            @Value("${enexia.zona-horaria:America/Argentina/Ushuaia}") String zona) {
        this.servicio = servicio;
        this.zona = ZoneId.of(zona);
    }

    @Scheduled(
            initialDelayString = "${enexia.notificaciones.recordatorio.retraso-inicial-ms:30000}",
            fixedDelayString = "${enexia.notificaciones.recordatorio.intervalo-ms:60000}")
    public void avisar() {
        try {
            servicio.avisarProximos(LocalDateTime.now(zona));
        } catch (RuntimeException ex) {
            // Un fallo en un tick no puede cancelar los siguientes.
            log.error("Fallo el recordatorio de inicio de eventos", ex);
        }
    }
}
