package com.ignis.prestamil.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Reloj del servidor. La fecha de operación de los movimientos sale de aquí y nunca del cliente
 * (RN-19); inyectarlo permite probar los casos de COCAE con sus fechas exactas.
 *
 * <p>Fijo en la hora del centro de México, sin depender de la zona del sistema operativo donde
 * corra el servidor: "hoy" es el mismo para el pase de almoneda, los movimientos y la pantalla del
 * cajero aunque el servidor esté en UTC.</p>
 */
@Configuration
public class ClockConfig {

    /** Zona de operación de las sucursales. Constante de compilación para usarla en {@code @Scheduled(zone)}. */
    public static final String ZONA_OPERACION = "America/Mexico_City";

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of(ZONA_OPERACION));
    }
}
