package com.ignis.prestamil.util;

import com.ignis.prestamil.exception.BadRequestException;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRElement;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.design.JRDesignElement;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleXlsxReportConfiguration;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generación de los reportes Jasper (REP-00): compila cada plantilla una sola vez, le pasa el encabezado
 * estándar ({@code jasper/reporte-encabezado.jrxml}) y exporta a PDF o a Excel (XLSX).
 *
 * <p>Para usar el encabezado, el reporte declara {@code P_ENCABEZADO} (JasperReport) y los parámetros de
 * {@link EncabezadoReporte#parametros()}, y en su banda de título pone el subreporte:</p>
 * <pre>{@code
 * <subreport>
 *     <reportElement x="0" y="0" width="<ancho de columna>" height="70"/>
 *     <parametersMapExpression><![CDATA[$P{REPORT_PARAMETERS_MAP}]]></parametersMapExpression>
 *     <dataSourceExpression><![CDATA[new net.sf.jasperreports.engine.JREmptyDataSource(1)]]></dataSourceExpression>
 *     <subreportExpression><![CDATA[$P{P_ENCABEZADO}]]></subreportExpression>
 * </subreport>
 * }</pre>
 * <p>{@link #llenar} pone {@code P_ENCABEZADO} ya escalado al ancho de columna del reporte, así que el mismo
 * encabezado sirve en carta vertical y horizontal.</p>
 */
public final class ReporteJasper {

    /** Parámetro con el subreporte del encabezado. Lo pone {@link #llenar}. */
    public static final String P_ENCABEZADO = "P_ENCABEZADO";

    private static final String PLANTILLA_ENCABEZADO = "jasper/reporte-encabezado.jrxml";

    // Las plantillas no cambian en runtime: se compilan una vez por ruta (y el encabezado, una vez por ancho)
    private static final Map<String, JasperReport> PLANTILLAS = new ConcurrentHashMap<>();
    private static final Map<Integer, JasperReport> ENCABEZADOS = new ConcurrentHashMap<>();

    private ReporteJasper() {
    }

    /**
     * Plantilla compilada desde el classpath, cacheada por ruta.
     *
     * @param ruta ruta del .jrxml, p. ej. {@code "jasper/corte-caja.jrxml"}
     * @return reporte compilado
     * @throws BadRequestException si la plantilla no existe o no compila
     */
    public static JasperReport plantilla(String ruta) {
        return PLANTILLAS.computeIfAbsent(ruta, ReporteJasper::compilar);
    }

    /**
     * Llena el reporte agregando a sus parámetros el encabezado estándar del ancho de su columna.
     *
     * @param reporte reporte compilado
     * @param params  parámetros del reporte (incluidos los del encabezado); no se modifica
     * @param datos   filas del detalle
     * @return reporte lleno, listo para exportar
     * @throws BadRequestException si Jasper no puede llenarlo
     */
    public static JasperPrint llenar(JasperReport reporte, Map<String, Object> params, JRDataSource datos) {
        Map<String, Object> conEncabezado = new HashMap<>(params);
        conEncabezado.put(P_ENCABEZADO, encabezado(reporte.getColumnWidth()));
        try {
            return JasperFillManager.fillReport(reporte, conEncabezado, datos);
        } catch (JRException e) {
            throw new BadRequestException("No se pudo generar el reporte " + reporte.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Reporte en PDF.
     *
     * @param reporte reporte compilado
     * @param params  parámetros del reporte
     * @param datos   filas del detalle
     * @return bytes del PDF
     */
    public static byte[] pdf(JasperReport reporte, Map<String, Object> params, JRDataSource datos) {
        JasperPrint print = llenar(reporte, params, datos);
        try {
            return JasperExportManager.exportReportToPdf(print);
        } catch (JRException e) {
            throw new BadRequestException("No se pudo exportar a PDF el reporte " + reporte.getName() + ": "
                    + e.getMessage());
        }
    }

    /**
     * Reporte en Excel (XLSX) a partir de la misma plantilla que el PDF: todas las páginas en una hoja,
     * sin renglones vacíos entre bandas y con los importes como números si el campo es numérico.
     *
     * @param reporte reporte compilado
     * @param params  parámetros del reporte
     * @param datos   filas del detalle
     * @return bytes del .xlsx
     */
    public static byte[] xlsx(JasperReport reporte, Map<String, Object> params, JRDataSource datos) {
        JasperPrint print = llenar(reporte, params, datos);
        SimpleXlsxReportConfiguration configuracion = new SimpleXlsxReportConfiguration();
        configuracion.setOnePagePerSheet(false);
        configuracion.setRemoveEmptySpaceBetweenRows(true);
        configuracion.setDetectCellType(true);
        configuracion.setWhitePageBackground(false);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        JRXlsxExporter exporter = new JRXlsxExporter();
        exporter.setExporterInput(new SimpleExporterInput(print));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(salida));
        exporter.setConfiguration(configuracion);
        try {
            exporter.exportReport();
        } catch (JRException e) {
            throw new BadRequestException("No se pudo exportar a Excel el reporte " + reporte.getName() + ": "
                    + e.getMessage());
        }
        return salida.toByteArray();
    }

    /**
     * Encabezado estándar del ancho indicado. La plantilla está diseñada al ancho de carta vertical; para
     * otro ancho se escalan posición y ancho de cada elemento, así lo centrado sigue centrado y lo que va
     * a la derecha sigue pegado al margen.
     *
     * @param ancho ancho de columna del reporte que lo contiene
     * @return subreporte compilado, cacheado por ancho
     */
    static JasperReport encabezado(int ancho) {
        return ENCABEZADOS.computeIfAbsent(ancho, ReporteJasper::compilarEncabezado);
    }

    private static JasperReport compilarEncabezado(int ancho) {
        try (InputStream is = new ClassPathResource(PLANTILLA_ENCABEZADO).getInputStream()) {
            JasperDesign diseno = JRXmlLoader.load(is);
            int anchoOriginal = diseno.getColumnWidth();
            if (ancho != anchoOriginal) {
                double factor = (double) ancho / anchoOriginal;
                for (JRElement elemento : diseno.getTitle().getElements()) {
                    JRDesignElement e = (JRDesignElement) elemento;
                    // Se escalan los dos bordes para que lo que llega al margen derecho siga llegando
                    int x = (int) Math.round(e.getX() * factor);
                    int derecha = (int) Math.round((e.getX() + e.getWidth()) * factor);
                    e.setX(x);
                    e.setWidth(derecha - x);
                }
                diseno.setPageWidth(ancho);
                diseno.setColumnWidth(ancho);
            }
            return JasperCompileManager.compileReport(diseno);
        } catch (IOException | JRException e) {
            throw new BadRequestException("No se pudo cargar el encabezado de los reportes: " + e.getMessage());
        }
    }

    private static JasperReport compilar(String ruta) {
        try (InputStream is = new ClassPathResource(ruta).getInputStream()) {
            return JasperCompileManager.compileReport(is);
        } catch (IOException | JRException e) {
            throw new BadRequestException("No se pudo cargar la plantilla " + ruta + ": " + e.getMessage());
        }
    }
}
