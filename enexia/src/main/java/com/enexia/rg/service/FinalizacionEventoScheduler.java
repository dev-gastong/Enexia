package com.enexia.rg.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Dispara periodicamente {@link FinalizacionEventoService}.
 *
 * Vive en su propio bean (y no como metodo {@code @Scheduled} del service)
 * para que cada {@code finalizar(id)} cruce el proxy y corra en su propia
 * transaccion: un evento que falla no deshace a los que ya se finalizaron.
 *
 * Se puede apagar con {@code enexia.eventos.finalizacion.habilitada=false}
 * (p. ej. en una prueba que no quiera que la fecha del sistema le cambie los
 * estados de debajo).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "enexia.eventos.finalizacion.habilitada",
        havingValue = "true", matchIfMissing = true)
public class FinalizacionEventoScheduler {

    private final FinalizacionEventoService finalizacionService;

    /**
     * La primera pasada corre unos segundos despues de arrancar, para que un
     * evento que termino mientras la aplicacion estaba apagada se finalice sin
     * esperar un intervalo completo.
     */
    @Scheduled(
            initialDelayString = "${enexia.eventos.finalizacion.retraso-inicial-ms:10000}",
            fixedDelayString = "${enexia.eventos.finalizacion.intervalo-ms:300000}")
    public void finalizarVencidos() {
        LocalDateTime ahora = LocalDateTime.now();
        List<Long> ids;
        try {
            ids = finalizacionService.idsVencidos(ahora);
        } catch (RuntimeException ex) {
            log.warn("No se pudo listar los eventos a finalizar: {}", ex.getMessage());
            return;
        }

        int finalizados = 0;
        for (Long id : ids) {
            try {
                if (finalizacionService.finalizar(id, ahora)) {
                    finalizados++;
                }
            } catch (RuntimeException ex) {
                log.warn("No se pudo finalizar el evento {}: {}", id, ex.getMessage());
            }
        }

        if (finalizados > 0) {
            log.info("Finalizacion automatica: {} evento(s) pasaron a FINALIZADO", finalizados);
        }
    }
}
