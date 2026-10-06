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
}
