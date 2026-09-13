package com.ignis.prestamil.service.calculo;

import com.ignis.prestamil.model.ParametrosSistema;
import com.ignis.prestamil.repository.ParametrosSistemaRepository;
import com.ignis.prestamil.util.Constantes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de la logica de fallback del cache del IVA. La logica de caching Caffeine
 * en si (evict, TTL, hits/misses) se prueba en un test de integracion aparte con contexto
 * Spring; aqui solo verifico que el metodo devuelve el valor correcto para cada escenario
 * (parametro OK, parametro con valor NULL, valor 0, registro ausente).
 */
@ExtendWith(MockitoExtension.class)
class ParametrosSistemaCacheTest {

    @Mock
    ParametrosSistemaRepository repository;

    ParametrosSistemaCache cache;

    @BeforeEach
    void setUp() {
        cache = new ParametrosSistemaCache(repository);
    }

    @Test
    void ivaConfigurado_devuelveValorDelParametro() {
        ParametrosSistema p = new ParametrosSistema();
        p.setId(Constantes.IVA);
        p.setValorNumerico(new BigDecimal("16.00"));
        when(repository.findById(Constantes.IVA)).thenReturn(Optional.of(p));

        assertThat(cache.getIvaPorcentaje()).isEqualByComparingTo("16.00");
    }

    @Test
    void ivaCambiadoA8_devuelveElNuevoValor() {
        // Prueba que el cache NO tiene un 16 hardcoded: si el repo dice 8, devuelve 8.
        ParametrosSistema p = new ParametrosSistema();
        p.setId(Constantes.IVA);
        p.setValorNumerico(new BigDecimal("8.00"));
        when(repository.findById(Constantes.IVA)).thenReturn(Optional.of(p));

        assertThat(cache.getIvaPorcentaje()).isEqualByComparingTo("8.00");
    }

    @Test
    void ivaValorNumericoNull_devuelveFallback16() {
        ParametrosSistema p = new ParametrosSistema();
        p.setId(Constantes.IVA);
        p.setValorNumerico(null); // configuracion incompleta
        when(repository.findById(Constantes.IVA)).thenReturn(Optional.of(p));

        assertThat(cache.getIvaPorcentaje()).isEqualByComparingTo("16");
    }

    @Test
    void ivaValorCero_devuelveFallback16NoCeroSilencioso() {
        ParametrosSistema p = new ParametrosSistema();
        p.setId(Constantes.IVA);
        p.setValorNumerico(BigDecimal.ZERO);
        when(repository.findById(Constantes.IVA)).thenReturn(Optional.of(p));

        // Regla explicita: nunca IVA=0 silencioso. La caja debe seguir cobrando algo razonable
        // y dejar warning en logs para que el admin corrija.
        assertThat(cache.getIvaPorcentaje()).isEqualByComparingTo("16");
    }

    @Test
    void ivaParametroAusente_devuelveFallback16() {
        when(repository.findById(Constantes.IVA)).thenReturn(Optional.empty());
        assertThat(cache.getIvaPorcentaje()).isEqualByComparingTo("16");
    }
}
