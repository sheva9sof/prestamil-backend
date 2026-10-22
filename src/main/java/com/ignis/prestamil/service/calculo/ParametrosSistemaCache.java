package com.ignis.prestamil.service.calculo;

import com.ignis.prestamil.repository.ParametrosSistemaRepository;
import com.ignis.prestamil.util.Constantes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Cache Caffeine (~30 min, evict manual) para el porcentaje de IVA vigente, leido desde
 * {@code parametros_sistema(id=8)}. Fallback documentado a 16% con {@code log.warn} si el
 * parametro falta o es 0/NULL: la caja no puede detenerse, pero deja rastro en logs para
 * corregir la configuracion.
 *
 * El evict se dispara desde {@link com.ignis.prestamil.service.ParametrosSistemaService#update}
 * via {@code @CacheEvict}.
 */
@Component
@Slf4j
public class ParametrosSistemaCache {

    static final String CACHE_NAME = "parametrosSistemaIva";
    static final BigDecimal FALLBACK_IVA = new BigDecimal("16");
    // D-14: default usado cuando el registro parametros_sistema(id=19) no existe o es NULL.
    static final BigDecimal FALLBACK_SANCION_SEMANAL = new BigDecimal("2");

    private final ParametrosSistemaRepository repository;

    public ParametrosSistemaCache(ParametrosSistemaRepository repository) {
        this.repository = repository;
    }

    /**
     * Devuelve el % IVA vigente. Fallback a 16 con log.warn si el parametro no esta
     * configurado (registro ausente, valor NULL o valor 0). Nunca devuelve 0 silencioso.
     *
     * @return porcentaje de IVA (ej. 16.00)
     */
    @Cacheable(CACHE_NAME)
    public BigDecimal getIvaPorcentaje() {
        return repository.findById(Constantes.IVA)
                .map(p -> {
                    BigDecimal v = p.getValorNumerico();
                    if (v == null || v.signum() == 0) {
                        log.warn("IVA no configurado (parametros_sistema id={}, valorNumerico={}). "
                                + "Usando fallback {}%.", Constantes.IVA, v, FALLBACK_IVA);
                        return FALLBACK_IVA;
                    }
                    return v;
                })
                .orElseGet(() -> {
                    log.warn("Parametro IVA ausente (parametros_sistema id={} no existe). "
                            + "Usando fallback {}%.", Constantes.IVA, FALLBACK_IVA);
                    return FALLBACK_IVA;
                });
    }

    /**
     * C-04: indica si el monto fijo de reposicion de contrato ya incluye IVA. Default true
     * cuando el parametro no existe o es NULL (lectura mas probable de la reunion 2026-09-30 con
     * Jorge). Lee {@code parametros_sistema(id=18).valor_numerico} donde 1 = incluye IVA, 0 = no.
     *
     * @return true si el monto fijo lleva IVA incluido; false si debe sumarse encima
     */
    @Cacheable(cacheNames = CACHE_NAME, key = "'reposicionIncluyeIva'")
    public boolean isReposicionIncluyeIva() {
        return repository.findById(Constantes.REPOSICION_INCLUYE_IVA)
                .map(p -> {
                    BigDecimal v = p.getValorNumerico();
                    // Null = sin configurar; mantenemos el default documentado en C-04
                    return v == null ? Boolean.TRUE : v.signum() != 0;
                })
                .orElse(Boolean.TRUE);
    }

    /**
     * D-14: devuelve el % de sanción semanal por default aplicado cuando al guardar un plazo
     * el request no incluye el campo. Lee {@code parametros_sistema(id=19)}; fallback a 2 con
     * {@code log.warn} si falta o es NULL/0, para que la configuración no quede en 0 silenciosa.
     */
    @Cacheable(cacheNames = CACHE_NAME, key = "'sancionSemanalDefault'")
    public BigDecimal getPorcSancionSemanalDefault() {
        return repository.findById(Constantes.SANCION_SEMANAL_DEFAULT)
                .map(p -> {
                    BigDecimal v = p.getValorNumerico();
                    if (v == null || v.signum() == 0) {
                        log.warn("% sanción semanal default no configurado (parametros_sistema id={},"
                                + " valorNumerico={}). Usando fallback {}%.",
                                Constantes.SANCION_SEMANAL_DEFAULT, v, FALLBACK_SANCION_SEMANAL);
                        return FALLBACK_SANCION_SEMANAL;
                    }
                    return v;
                })
                .orElseGet(() -> {
                    log.warn("Parametro % sancion semanal default ausente (parametros_sistema id={}). "
                            + "Usando fallback {}%.",
                            Constantes.SANCION_SEMANAL_DEFAULT, FALLBACK_SANCION_SEMANAL);
                    return FALLBACK_SANCION_SEMANAL;
                });
    }
}
