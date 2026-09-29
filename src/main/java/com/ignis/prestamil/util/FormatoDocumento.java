package com.ignis.prestamil.util;

import com.ignis.prestamil.model.Cliente;
import com.ignis.prestamil.model.Direccion;
import com.ignis.prestamil.model.Empresa;
import com.ignis.prestamil.model.Sucursal;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Formatos de texto comunes a los documentos impresos (contrato y ticket de movimiento): nombres,
 * domicilios e importes.
 */
public final class FormatoDocumento {

    private FormatoDocumento() {
    }

    /** Importe con signo y separador de miles: "$1,290.92". Vacío se imprime como $0.00. */
    public static String money(BigDecimal valor) {
        // DecimalFormat no es thread-safe: una instancia por llamada
        DecimalFormat formato = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return "$" + formato.format(valor != null ? valor : BigDecimal.ZERO);
    }

    public static String nombreCompleto(Cliente c) {
        if (c == null) return "";
        return (nz(c.getNombre()) + " " + nz(c.getApellidoPaterno()) + " " + nz(c.getApellidoMaterno())).trim();
    }

    public static String domicilio(Direccion d) {
        if (d == null) return "";
        return (nz(d.getCalle()) + " " + nz(d.getNumeroExterior()) + ", " + nz(d.getColonia())
                + ", " + nz(d.getCiudad()) + ", " + nz(d.getEstado()) + " C.P. " + nz(d.getCodigoPostal()))
                .replaceAll("\\s+,", ",").trim();
    }

    public static String domicilio(Sucursal s) {
        if (s == null) return "";
        return (nz(s.getCalle()) + " " + nz(s.getNoExterior()) + ", " + nz(s.getColonia())
                + ", " + nz(s.getMunicipio()) + ", " + nz(s.getEstado()) + " C.P. " + nz(s.getCp()))
                .replaceAll("\\s+,", ",").trim();
    }

    /** Domicilio fiscal de la razón social; omite las partes vacías. */
    public static String domicilioFiscal(Empresa e) {
        if (e == null) return "";
        String calle = Stream.of(e.getCalle(), e.getNoExterior(),
                        e.getNoInterior() != null && !e.getNoInterior().isBlank() ? "Int. " + e.getNoInterior() : null)
                .filter(parte -> parte != null && !parte.isBlank())
                .collect(Collectors.joining(" "));
        return Stream.of(calle, e.getColonia(), e.getDelegacion(),
                        e.getCp() != null && !e.getCp().isBlank() ? "C.P. " + e.getCp() : null, e.getEstado())
                .filter(Objects::nonNull)
                .filter(parte -> !parte.isBlank())
                .collect(Collectors.joining(", "));
    }

    private static String nz(String s) {
        return s != null ? s : "";
    }
}
