package com.ignis.prestamil.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Configura el CacheManager con Caffeine
     * Caffeine es una librería de cache local en memoria, rápida y eficiente
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("sucursal");
        
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(100)                    // Máximo 100 entradas en cache
                .expireAfterWrite(30, TimeUnit.MINUTES)  // Expira después de 30 minutos sin escritura
                .recordStats());                     // Habilitar estadísticas para monitoreo
        
        return cacheManager;
    }

}

