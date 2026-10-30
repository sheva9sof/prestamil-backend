package com.ignis.prestamil.service;

import com.ignis.prestamil.config.ClockConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Disparadores automaticos del pase de almoneda: al arrancar la aplicacion (por si estuvo apagada
 * uno o varios dias) y todos los dias a las 00:05 hora del centro de Mexico. Abrir turno y
 * consultar resultados lo disparan aparte. {@link PaseAlmonedaService#ejecutarPendientes()} es
 * idempotente, asi que las llamadas repetidas no hacen nada.
 */
@Component
@RequiredArgsConstructor
public class PaseAlmonedaProgramador {

    private final PaseAlmonedaService paseAlmonedaService;

    /** Pone al dia los pases que no corrieron mientras la aplicacion estuvo apagada. */
    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        paseAlmonedaService.ejecutarPendientes();
    }

    /** Pase del dia, a las 00:05. */
    @Scheduled(cron = "0 5 0 * * *", zone = ClockConfig.ZONA_OPERACION)
    public void diario() {
        paseAlmonedaService.ejecutarPendientes();
    }
}
