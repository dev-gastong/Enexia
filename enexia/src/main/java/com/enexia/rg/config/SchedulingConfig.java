package com.enexia.rg.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita las tareas periodicas {@code @Scheduled} (finalizacion de eventos vencidos). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
