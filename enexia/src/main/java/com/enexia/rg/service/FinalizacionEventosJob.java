package com.enexia.rg.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Disparador periodico de {@link FinalizacionEventosService}.
 *
 * Es un bean aparte a proposito: el service abre su transaccion por proxy, y
 * una llamada desde su propia clase no pasa por el proxy.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FinalizacionEventosJob {

    private final FinalizacionEventosService finalizacionEventosService;

    @Scheduled(initialDelay = 20_000, fixedDelay = 300_000)
    public void barrido() {
        try {
            int n = finalizacionEventosService.finalizarVencidos(null);
            if (n > 0) {
                log.info("Barrido de finalizacion: {} evento(s) pasaron a FINALIZADO", n);
            }
        } catch (RuntimeException ex) {
            log.error("Fallo el barrido de finalizacion de eventos", ex);
        }
    }
}
