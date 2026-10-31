package com.ignis.prestamil.util;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.model.Empresa;
import com.ignis.prestamil.model.Sucursal;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import net.sf.jasperreports.engine.JRElement;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Infraestructura común de reportes (REP-00): encabezado estándar escalado al ancho del reporte,
 * caché de plantillas y exportación a PDF y XLSX.
 */
class ReporteJasperTest {

    private static final String PLANTILLA = "jasper/reporte-prueba.jrxml";

    @Test
    void encabezado_horizontal_escalaLosElementosAlAnchoDelReporte() {
        JasperReport encabezado = ReporteJasper.encabezado(736);

        assertThat(encabezado.getColumnWidth()).isEqualTo(736);
        JRElement[] elementos = encabezado.getTitle().getElements();
        // Lo que ocupaba todo el ancho lo sigue ocupando y nada se sale del margen
        assertThat(Arrays.stream(elementos).mapToInt(e -> e.getX() + e.getWidth()).max()).hasValue(736);
        assertThat(Arrays.stream(elementos).filter(e -> e.getX() == 0 && e.getWidth() == 736)).hasSizeGreaterThan(3);
    }

    @Test
    void encabezado_mismoAncho_seCompilaUnaVez() {
        assertThat(ReporteJasper.encabezado(556)).isSameAs(ReporteJasper.encabezado(556));
        assertThat(ReporteJasper.encabezado(556).getColumnWidth()).isEqualTo(556);
    }

    @Test
    void plantilla_seCompilaUnaVez() {
        assertThat(ReporteJasper.plantilla(PLANTILLA)).isSameAs(ReporteJasper.plantilla(PLANTILLA));
    }

    @Test
    void plantilla_inexistente_lanzaBadRequest() {
        assertThatThrownBy(() -> ReporteJasper.plantilla("jasper/no-existe.jrxml"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("jasper/no-existe.jrxml");
    }

    @Test
    void pdf_imprimeElEncabezadoEstandarCompleto() throws Exception {
        Map<String, Object> params = encabezado().parametros();

        byte[] pdf = ReporteJasper.pdf(ReporteJasper.plantilla(PLANTILLA), params, datos());

        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        PdfReader reader = new PdfReader(pdf);
        String texto = new PdfTextExtractor(reader).getTextFromPage(1);
        reader.close();
        assertThat(texto)
                .contains("PRESTAMIL")
                .contains("PRESTAMIL DEL SUR SA DE CV")
                .contains("Sucursal: 7 Pinotepa 2")
                .contains("CORTE DE CAJA")
                .contains("CON FECHA DEL 17/09/2026")
                .contains("Impreso: 17/09/2026 18:42")
                .contains("RPT.DR-1.1.0")
                .contains("Finiquitos")
                .contains("25,017.29");
        // El mapa del servicio no se toca: P_ENCABEZADO solo vive en la copia que se llena
        assertThat(params).doesNotContainKey(ReporteJasper.P_ENCABEZADO);
    }

    @Test
    void xlsx_generaUnLibroDeExcel() {
        byte[] xlsx = ReporteJasper.xlsx(ReporteJasper.plantilla(PLANTILLA), encabezado().parametros(), datos());

        // Un .xlsx es un ZIP: empieza con "PK"
        assertThat(new String(xlsx, 0, 2)).isEqualTo("PK");
    }

    @Test
    void estandar_tomaEmpresaRazonSocialYSucursal() {
        EncabezadoReporte e = encabezado();

        assertThat(e.empresa()).isEqualTo("PRESTAMIL");
        assertThat(e.razonSocial()).isEqualTo("PRESTAMIL DEL SUR SA DE CV");
        assertThat(e.sucursal()).isEqualTo("Sucursal: 7 Pinotepa 2");
        assertThat(e.parametros()).containsEntry("P_TITULO", "CORTE DE CAJA")
                .containsEntry("P_CODIGO_RPT", "RPT.DR-1.1.0");
    }

    @Test
    void estandar_sinSucursal_usaPrestamilYDejaLaSucursalVacia() {
        EncabezadoReporte e = EncabezadoReporte.estandar(null, "T", "F", LocalDateTime.of(2026, 9, 17, 8, 0), null);

        assertThat(e.empresa()).isEqualTo("PRESTAMIL");
        assertThat(e.razonSocial()).isNull();
        assertThat(e.sucursal()).isEmpty();
    }

    private static EncabezadoReporte encabezado() {
        Empresa empresa = new Empresa();
        empresa.setNombre("Prestamil");
        empresa.setRazonSocial("PRESTAMIL DEL SUR SA DE CV");
        Sucursal sucursal = new Sucursal();
        sucursal.setNumeroSucursal(7);
        sucursal.setNombre("Pinotepa 2");
        sucursal.setEmpresa(empresa);
        return EncabezadoReporte.estandar(sucursal, "CORTE DE CAJA", "CON FECHA DEL 17/09/2026",
                LocalDateTime.of(2026, 9, 17, 18, 42), "RPT.DR-1.1.0");
    }

    private static JRMapCollectionDataSource datos() {
        List<Map<String, ?>> filas = List.of(
                Map.of("concepto", "Finiquitos", "importe", new BigDecimal("25017.29")),
                Map.of("concepto", "Refrendos", "importe", new BigDecimal("15387.26")));
        return new JRMapCollectionDataSource(filas);
    }
}
