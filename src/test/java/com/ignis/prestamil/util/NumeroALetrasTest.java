package com.ignis.prestamil.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NumeroALetrasTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', value = {
            // Nota de refrendo del contrato 1493 de COCAE
            "95.92      | NOVENTA Y CINCO PESOS 92/100 M.N.",
            "0          | CERO PESOS 00/100 M.N.",
            "1          | UN PESO 00/100 M.N.",
            "0.50       | CERO PESOS 50/100 M.N.",
            "15         | QUINCE PESOS 00/100 M.N.",
            "16         | DIECISÉIS PESOS 00/100 M.N.",
            "21         | VEINTIÚN PESOS 00/100 M.N.",
            "22         | VEINTIDÓS PESOS 00/100 M.N.",
            "31         | TREINTA Y UN PESOS 00/100 M.N.",
            "100        | CIEN PESOS 00/100 M.N.",
            "101        | CIENTO UN PESOS 00/100 M.N.",
            "325.68     | TRESCIENTOS VEINTICINCO PESOS 68/100 M.N.",
            "500        | QUINIENTOS PESOS 00/100 M.N.",
            "1000       | MIL PESOS 00/100 M.N.",
            "1290.92    | MIL DOSCIENTOS NOVENTA PESOS 92/100 M.N.",
            "2375.69    | DOS MIL TRESCIENTOS SETENTA Y CINCO PESOS 69/100 M.N.",
            "21000      | VEINTIÚN MIL PESOS 00/100 M.N.",
            "100000     | CIEN MIL PESOS 00/100 M.N.",
            "101001     | CIENTO UN MIL UN PESOS 00/100 M.N.",
            "1000000    | UN MILLÓN DE PESOS 00/100 M.N.",
            "2000000    | DOS MILLONES DE PESOS 00/100 M.N.",
            "1500000    | UN MILLÓN QUINIENTOS MIL PESOS 00/100 M.N.",
            "999999999.99 | NOVECIENTOS NOVENTA Y NUEVE MILLONES NOVECIENTOS NOVENTA Y NUEVE MIL NOVECIENTOS NOVENTA Y NUEVE PESOS 99/100 M.N."
    })
    void importe(String importe, String esperado) {
        assertThat(NumeroALetras.importe(new BigDecimal(importe))).isEqualTo(esperado);
    }

    @Test
    void redondeaACentavos() {
        assertThat(NumeroALetras.importe(new BigDecimal("95.915"))).isEqualTo("NOVENTA Y CINCO PESOS 92/100 M.N.");
        assertThat(NumeroALetras.importe(new BigDecimal("99.999"))).isEqualTo("CIEN PESOS 00/100 M.N.");
    }

    @Test
    void negativoOFueraDeRango_rechaza() {
        assertThatThrownBy(() -> NumeroALetras.importe(new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NumeroALetras.importe(new BigDecimal("1000000000")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
