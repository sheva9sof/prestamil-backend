package com.ignis.prestamil.util;

import java.nio.charset.StandardCharsets;

/** Armado de reportes en CSV que Excel abre bien (REP-00): campos escapados y BOM UTF-8. */
public final class CsvReporte {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private CsvReporte() {
    }

    /** Escapa un campo CSV: comillas dobles si contiene coma, comilla o salto de linea. Nulo = vacío. */
    public static String campo(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        boolean needQuotes = s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0;
        String escaped = s.replace("\"", "\"\"");
        return needQuotes ? "\"" + escaped + "\"" : escaped;
    }

    /** Antepone el BOM UTF-8 para que Excel detecte el encoding al abrir el .csv. */
    public static byte[] conBom(String contenido) {
        byte[] body = contenido.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, out, 0, BOM.length);
        System.arraycopy(body, 0, out, BOM.length, body.length);
        return out;
    }
}
