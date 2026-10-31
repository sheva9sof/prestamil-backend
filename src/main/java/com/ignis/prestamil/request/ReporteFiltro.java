package com.ignis.prestamil.request;

import com.ignis.prestamil.exception.BadRequestException;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Filtros comunes de los reportes (REP-00): ramo y rango de fechas. Se recibe como query params
 * ({@code ?ramo=&desde=&hasta=}) poniendo el record como parámetro del método del controlador.
 *
 * @param ramo  id de {@code tipo_prenda} (ALHAJA, VARIOS, PLATAS, AUTOS/MOTOS; Q-05); {@code null} = todos
 * @param desde primer día del rango (incluido)
 * @param hasta último día del rango (incluido)
 */
public record ReporteFiltro(
        Integer ramo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta
) {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Completa las fechas que falten: sin {@code desde} se usa hoy y sin {@code hasta}, el mismo día que
     * {@code desde}. Así un reporte de un solo día solo necesita {@code desde}.
     *
     * @param hoy fecha de hoy del servidor
     * @return filtro con las dos fechas
     * @throws BadRequestException si {@code desde} es posterior a {@code hasta}
     */
    public ReporteFiltro conDefaults(LocalDate hoy) {
        LocalDate inicio = desde != null ? desde : hoy;
        LocalDate fin = hasta != null ? hasta : inicio;
        if (inicio.isAfter(fin)) {
            throw new BadRequestException("La fecha inicial (" + inicio.format(FECHA)
                    + ") es posterior a la final (" + fin.format(FECHA) + ")");
        }
        return new ReporteFiltro(ramo, inicio, fin);
    }

    /** Sin ramo: el reporte incluye todos los tipos de prenda. */
    public boolean todosLosRamos() {
        return ramo == null;
    }

    /**
     * Línea de fechas del encabezado estándar: "CON FECHA DEL 17/09/2026" para un día y
     * "CON FECHA DEL 01/09/2026 AL 17/09/2026" para un rango. Llamar después de {@link #conDefaults}.
     *
     * @return texto para {@code P_FECHAS}
     */
    public String textoFechas() {
        String texto = "CON FECHA DEL " + desde.format(FECHA);
        return hasta == null || hasta.equals(desde) ? texto : texto + " AL " + hasta.format(FECHA);
    }
}
