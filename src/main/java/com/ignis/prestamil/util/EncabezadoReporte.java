package com.ignis.prestamil.util;

import com.ignis.prestamil.model.Empresa;
import com.ignis.prestamil.model.Sucursal;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Valores del encabezado estándar de los reportes (REP-00), que imprime {@code jasper/reporte-encabezado.jrxml}:
 * PRESTAMIL / razón social / sucursal / título / rango de fechas / fecha y hora / código RPT.
 *
 * <p>Uso típico: {@code EncabezadoReporte.estandar(...).parametros()} y sobre ese mapa se agregan los
 * parámetros propios del reporte. Razón social y código RPT pueden ir en {@code null}: el subreporte no
 * les deja lugar.</p>
 *
 * @param empresa     nombre comercial en mayúsculas ("PRESTAMIL")
 * @param razonSocial razón social de la empresa; opcional
 * @param sucursal    línea de la sucursal
 * @param titulo      título del reporte
 * @param fechas      línea de fechas, a la izquierda ("CON FECHA DEL …")
 * @param impreso     fecha y hora de impresión, a la derecha
 * @param codigoRpt   código del reporte de COCAE ("RPT.DR-1.1.0"), en la esquina; opcional
 */
public record EncabezadoReporte(
        String empresa,
        String razonSocial,
        String sucursal,
        String titulo,
        String fechas,
        String impreso,
        String codigoRpt
) {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * Encabezado de los reportes de COCAE: empresa y razón social de la sucursal, "Sucursal: 7 Centro",
     * título, fechas, impresión y código RPT.
     *
     * @param sucursal  sucursal del reporte (puede ser nula)
     * @param titulo    título del reporte
     * @param fechas    línea de fechas; normalmente {@code ReporteFiltro.textoFechas()}
     * @param ahora     fecha y hora de impresión (del {@code Clock})
     * @param codigoRpt código RPT del reporte; puede ser nulo
     * @return encabezado listo para {@link #parametros()}
     */
    public static EncabezadoReporte estandar(Sucursal sucursal, String titulo, String fechas,
                                             LocalDateTime ahora, String codigoRpt) {
        Empresa empresa = sucursal != null ? sucursal.getEmpresa() : null;
        String lineaSucursal = sucursal == null ? "" : ("Sucursal: "
                + (sucursal.getNumeroSucursal() != null ? sucursal.getNumeroSucursal() + " " : "")
                + nz(sucursal.getNombre())).trim();
        return new EncabezadoReporte(empresa(sucursal), empresa != null ? empresa.getRazonSocial() : null,
                lineaSucursal, titulo, fechas, impreso(ahora), codigoRpt);
    }

    /** Nombre de la empresa de la sucursal en mayúsculas; "PRESTAMIL" si no hay. */
    public static String empresa(Sucursal sucursal) {
        Empresa empresa = sucursal != null ? sucursal.getEmpresa() : null;
        return empresa != null && empresa.getNombre() != null ? empresa.getNombre().toUpperCase() : "PRESTAMIL";
    }

    /** "Impreso: 17/09/2026 18:42". */
    public static String impreso(LocalDateTime ahora) {
        return "Impreso: " + ahora.format(FECHA_HORA);
    }

    /**
     * Parámetros del encabezado para el reporte que lo contiene. El mapa es mutable: el servicio le agrega
     * los parámetros propios del reporte antes de llenarlo.
     *
     * @return mapa con P_EMPRESA, P_RAZON_SOCIAL, P_SUCURSAL, P_TITULO, P_FECHAS, P_IMPRESO y P_CODIGO_RPT
     */
    public Map<String, Object> parametros() {
        Map<String, Object> params = new HashMap<>();
        params.put("P_EMPRESA", empresa);
        params.put("P_RAZON_SOCIAL", razonSocial);
        params.put("P_SUCURSAL", sucursal);
        params.put("P_TITULO", titulo);
        params.put("P_FECHAS", fechas);
        params.put("P_IMPRESO", impreso);
        params.put("P_CODIGO_RPT", codigoRpt);
        return params;
    }

    private static String nz(String s) {
        return s != null ? s : "";
    }
}
