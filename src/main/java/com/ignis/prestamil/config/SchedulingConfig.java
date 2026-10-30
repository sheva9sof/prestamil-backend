package com.ignis.prestamil.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita las tareas {@code @Scheduled} (pase de almoneda de las 00:05). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
