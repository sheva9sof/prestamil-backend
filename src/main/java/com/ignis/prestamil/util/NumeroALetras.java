package com.ignis.prestamil.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Importe con letra como lo imprimen las notas de COCAE: "NOVENTA Y CINCO PESOS 92/100 M.N.".
 * Lo usan la ventana de Cobro (vía la cotización) y el ticket, para que ambos digan lo mismo.
 */
public final class NumeroALetras {

    private static final long MAXIMO = 999_999_999L;

    private static final String[] HASTA_VEINTINUEVE = {
            "", "UN", "DOS", "TRES", "CUATRO", "CINCO", "SEIS", "SIETE", "OCHO", "NUEVE",
            "DIEZ", "ONCE", "DOCE", "TRECE", "CATORCE", "QUINCE", "DIECISÉIS", "DIECISIETE", "DIECIOCHO", "DIECINUEVE",
            "VEINTE", "VEINTIÚN", "VEINTIDÓS", "VEINTITRÉS", "VEINTICUATRO", "VEINTICINCO", "VEINTISÉIS",
            "VEINTISIETE", "VEINTIOCHO", "VEINTINUEVE"
    };
    private static final String[] DECENAS = {
            "", "", "", "TREINTA", "CUARENTA", "CINCUENTA", "SESENTA", "SETENTA", "OCHENTA", "NOVENTA"
    };
    private static final String[] CENTENAS = {
            "", "CIENTO", "DOSCIENTOS", "TRESCIENTOS", "CUATROCIENTOS", "QUINIENTOS", "SEISCIENTOS",
            "SETECIENTOS", "OCHOCIENTOS", "NOVECIENTOS"
    };

    private NumeroALetras() {
    }

    /**
     * Importe en pesos con letra, centavos en número y "M.N.".
     *
     * @param importe importe no negativo; se redondea a centavos
     * @return p. ej. "DOS MIL TRESCIENTOS SETENTA Y CINCO PESOS 69/100 M.N."
     * @throws IllegalArgumentException si el importe es negativo o mayor a 999,999,999.99
     */
    public static String importe(BigDecimal importe) {
        BigDecimal redondeado = importe.setScale(2, RoundingMode.HALF_UP);
        if (redondeado.signum() < 0 || redondeado.longValue() > MAXIMO) {
            throw new IllegalArgumentException("Importe fuera de rango para convertir a letra: " + importe);
        }
        long pesos = redondeado.longValue();
        int centavos = redondeado.remainder(BigDecimal.ONE).movePointRight(2).intValue();

        String letras = pesos == 0 ? "CERO" : entero(pesos);
        // "UN MILLÓN DE PESOS", pero "UN MILLÓN QUINIENTOS MIL PESOS"
        String de = pesos % 1_000_000 == 0 && pesos > 0 ? "DE " : "";
        String moneda = pesos == 1 ? "PESO" : "PESOS";
        return String.format("%s %s%s %02d/100 M.N.", letras, de, moneda, centavos);
    }

    /** Entero con apócope ("UN", "VEINTIÚN"): siempre va seguido de un sustantivo (pesos, mil, millones). */
    private static String entero(long n) {
        int millones = (int) (n / 1_000_000);
        int miles = (int) (n / 1_000 % 1_000);
        int unidades = (int) (n % 1_000);

        StringBuilder sb = new StringBuilder();
        if (millones > 0) {
            sb.append(millones == 1 ? "UN MILLÓN" : centenas(millones) + " MILLONES");
        }
        if (miles > 0) {
            sb.append(sb.isEmpty() ? "" : " ").append(miles == 1 ? "MIL" : centenas(miles) + " MIL");
        }
        if (unidades > 0) {
            sb.append(sb.isEmpty() ? "" : " ").append(centenas(unidades));
        }
        return sb.toString();
    }

    private static String centenas(int n) {
        if (n == 100) {
            return "CIEN";
        }
        String centena = CENTENAS[n / 100];
        String resto = decenas(n % 100);
        if (centena.isEmpty()) {
            return resto;
        }
        return resto.isEmpty() ? centena : centena + " " + resto;
    }

    private static String decenas(int n) {
        if (n < 30) {
            return HASTA_VEINTINUEVE[n];
        }
        int unidad = n % 10;
        return unidad == 0 ? DECENAS[n / 10] : DECENAS[n / 10] + " Y " + HASTA_VEINTINUEVE[unidad];
    }
}
