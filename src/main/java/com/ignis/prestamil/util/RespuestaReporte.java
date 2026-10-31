package com.ignis.prestamil.util;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/**
 * Respuestas HTTP de los reportes (REP-00): el PDF se abre en el visor ("Pantalla") y el Excel se
 * descarga ("Exportar"). Excel puede ir como XLSX (generado de la misma plantilla Jasper) o como CSV.
 */
public final class RespuestaReporte {

    /** Tipo MIME de los .xlsx. */
    public static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private RespuestaReporte() {
    }

    /** PDF para mostrar en el navegador ({@code inline}). */
    public static ResponseEntity<byte[]> pdf(byte[] body, String nombreArchivo) {
        return archivo(body, MediaType.APPLICATION_PDF, "inline", nombreArchivo);
    }

    /** Excel (.xlsx) para descargar. */
    public static ResponseEntity<byte[]> xlsx(byte[] body, String nombreArchivo) {
        return archivo(body, XLSX, "attachment", nombreArchivo);
    }

    /** CSV UTF-8 para descargar; Excel lo abre nativo si trae BOM ({@link CsvReporte#conBom}). */
    public static ResponseEntity<byte[]> csv(byte[] body, String nombreArchivo) {
        return archivo(body, CSV, "attachment", nombreArchivo);
    }

    private static ResponseEntity<byte[]> archivo(byte[] body, MediaType tipo, String disposicion, String nombre) {
        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion + "; filename=\"" + nombre + "\"")
                .body(body);
    }
}
