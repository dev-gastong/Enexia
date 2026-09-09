package com.enexia.rg.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Pool de hilos para el trabajo en segundo plano (RF-2.2, RF-5.1, RF-5.3).
 *
 * POR QUE UN EJECUTOR PROPIO Y NO EL DE SPRING POR DEFECTO
 * Sin un bean {@code Executor} declarado, {@code @Async} usa un
 * SimpleAsyncTaskExecutor: crea UN HILO NUEVO POR TAREA y no los reutiliza. Con
 * un pico de creacion de eventos eso significa un hilo por evento, sin techo,
 * hasta agotar la memoria del proceso. Un pool acotado convierte ese pico en
 * una cola: las tareas esperan, pero el servidor no se cae.
 *
 * ALCANCE Y LIMITE CONOCIDO
 * La cola vive en memoria. Si el proceso se reinicia con tareas pendientes, los
 * eventos quedan EN_PROCESO para siempre (el DFD 2.5 lo anticipa: "Si API cae ->
 * evento queda EN_PROCESO / manual recovery"). Para el MVP alcanza; el paso
 * siguiente natural es mover la cola a RabbitMQ o Redis, que sobrevive al
 * reinicio. El punto de cambio seria unicamente esta clase mas el disparo en
 * ModeracionEventoService.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * {@code @Async("ejecutorEnexia")} referencia este bean por nombre.
     *
     * Los tamanos son conservadores a proposito: el trabajo asincrono de Enexia
     * es sobre todo espera de red (Cloudinary, SMTP), no calculo. Mas hilos no
     * lo harian mas rapido, solo abririan mas conexiones simultaneas contra
     * servicios de terceros que tienen sus propios limites de tasa.
     */
    @Bean(name = "ejecutorEnexia")
    public Executor ejecutorEnexia() {
        ThreadPoolTaskExecutor ejecutor = new ThreadPoolTaskExecutor();
        ejecutor.setCorePoolSize(2);
        ejecutor.setMaxPoolSize(5);
        ejecutor.setQueueCapacity(100);
        // Prefijo reconocible: en un stack trace o en el log se distingue de
        // inmediato una tarea de fondo de una peticion HTTP.
        ejecutor.setThreadNamePrefix("enexia-async-");
        // Al apagar la aplicacion, espera a que terminen las tareas en curso en
        // vez de cortarlas: un evento a mitad de moderar quedaria EN_PROCESO.
        ejecutor.setWaitForTasksToCompleteOnShutdown(true);
        ejecutor.setAwaitTerminationSeconds(30);
        ejecutor.initialize();
        return ejecutor;
    }
}
