package com.ignis.prestamil.service.calculo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Formato con el que el cierre de día congela un {@link CorteCajaResultado} en {@code cierre_caja_diario}
 * (CAJ-06). Tiene su propio {@link ObjectMapper}, y no el de Spring, para que lo guardado no cambie si
 * cambia la configuración de la API: fechas ISO (yyyy-MM-dd) y propiedades desconocidas ignoradas, así un
 * cierre viejo se sigue leyendo aunque el resultado gane campos.
 */
public final class CorteCajaJson {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private CorteCajaJson() {
    }

    /**
     * Corte en JSON.
     *
     * @param corte corte con cifras
     * @return el JSON que se guarda en el cierre
     */
    public static String escribir(CorteCajaResultado corte) {
        try {
            return MAPPER.writeValueAsString(corte);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo congelar el corte del " + corte.fecha(), e);
        }
    }

    /**
     * Corte congelado de un cierre.
     *
     * @param json el JSON guardado al cerrar
     * @return el corte tal como estaba al cerrar
     */
    public static CorteCajaResultado leer(String json) {
        try {
            return MAPPER.readValue(json, CorteCajaResultado.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("El corte congelado de un cierre de caja no se puede leer", e);
        }
    }
}
