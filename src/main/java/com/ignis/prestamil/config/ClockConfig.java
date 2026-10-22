package com.ignis.prestamil.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Reloj del servidor. La fecha de operación de los movimientos sale de aquí y nunca del cliente
 * (RN-19); inyectarlo permite probar los casos de COCAE con sus fechas exactas.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
