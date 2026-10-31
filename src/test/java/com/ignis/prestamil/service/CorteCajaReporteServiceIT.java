package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.model.Empresa;
import com.ignis.prestamil.model.RenglonCorte;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.repository.ConfiguracionRepository;
import com.ignis.prestamil.request.ReporteFiltro;
import com.ignis.prestamil.service.calculo.CorteCajaSemilla;
import com.ignis.prestamil.service.calculo.CorteCajaService;
import com.ignis.prestamil.service.calculo.SaldoArranqueConfiguracionProvider;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.ignis.prestamil.service.calculo.CorteCajaSemilla.DIA_5_4;
import static com.ignis.prestamil.service.calculo.CorteCajaSemilla.SUCURSAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Corte de caja diario en PDF y Excel (REP-04) contra H2, con el motor real ({@code CorteCajaService}).
 * El caso principal genera el reporte del 17/09/2026 de Pinotepa 2 y compara cada cifra impresa contra la
 * sección 5.4 de PLAN-VENTAS-CAJA-REPORTES, con ±0.02 donde COCAE redondea distinto (como CAJ-05).
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CorteCajaReporteService.class, CorteCajaConsultaService.class, CorteCajaService.class,
        ResolutorRenglonCorte.class, SaldoArranqueConfiguracionProvider.class,
        CorteCajaReporteServiceIT.Reloj.class})
class CorteCajaReporteServiceIT {

    private static final BigDecimal COCAE = new BigDecimal("0.02");
    private static final ReporteFiltro DEL_17 = new ReporteFiltro(null, DIA_5_4, null);

    /** El reporte se imprime el 17/09/2026 a las 18:42: ese día es "hoy" y el cajero puede sacarlo. */
    @TestConfiguration
    static class Reloj {
        @Bean
        Clock clock() {
            ZoneId mx = ZoneId.of("America/Mexico_City");
            return Clock.fixed(DIA_5_4.atTime(18, 42).atZone(mx).toInstant(), mx);
        }
    }

    /** Una cifra esperada en las dos columnas de importes; {@code null} = celda vacía. */
    private record Cifra(String etiqueta, String importe1, String importe2) {
    }

    /** Bloque superior de 5.4: efectivo de cada renglón en la columna de ingresos o de egresos. */
    private static final List<Cifra> MOVIMIENTOS_5_4 = List.of(
            new Cifra("DOTACIONES", "18861.85", null),
            new Cifra("EMPEÑOS", null, "32370.00"),
            new Cifra("INDEMNIZACIONES", null, "0.00"),
            new Cifra("DEVOLUCIONES", null, "0.00"),
            new Cifra("DEMASÍAS", null, "0.00"),
            new Cifra("ABONOS PARCIALES", "0.00", null),
            new Cifra("ABONOS A CAPITAL", "10489.00", null),
            new Cifra("APARTADOS / ABONOS DE APARTADOS", "0.00", null),
            new Cifra("FINIQUITOS", "25017.29", null),
            new Cifra("FINIQUITOS EXTEMPORÁNEOS", "0.00", null),
            new Cifra("REFRENDOS", "15387.26", null),
            new Cifra("REFRENDOS EXTEMPORÁNEOS", "4153.24", null),
            new Cifra("REPOSICIONES", "0.00", null),
            new Cifra("VENTAS", "2350.87", null),
            new Cifra("RECOLECCIÓN", null, "43889.50"),
            // COCAE imprime 76,259.50 de ingresos; la suma exacta de sus renglones es 76,259.51
            new Cifra("TOTALES", "76259.50", "76259.50"));

    /** Bloque de caja de 5.4: bóveda y ventanilla, con el signo con el que se mueve cada una. */
    private static final List<Cifra> CAJA_5_4 = List.of(
            new Cifra("SALDO ANTERIOR", "41013.11", null),
            new Cifra("MINISTRACIONES", "0.00", null),
            new Cifra("CONCENTRACIONES", "-1000.00", null),
            new Cifra("BÓVEDA", "40013.11", null),
            new Cifra("DOTACIONES", "-18861.85", "18861.85"),
            new Cifra("INGRESOS", null, "57397.65"),
            new Cifra("EGRESOS", null, "-32370.00"),
            new Cifra("RECOLECCIONES", "43889.50", "-43889.50"),
            new Cifra("DIFERENCIA", null, "0.00"),
            // COCAE imprime 65,040.77; el cálculo exacto da 65,040.76
            new Cifra("SALDO FINAL", "65040.77", null));

    @Autowired CorteCajaReporteService corteCajaReporteService;
    @Autowired ConfiguracionRepository configuracionRepository;
    @Autowired EntityManager em;

    private CorteCajaSemilla semilla;

    @BeforeEach
    void sembrar() {
        Empresa empresa = new Empresa();
        empresa.setId(1);
        empresa.setNombre("Prestamil");
        empresa.setRazonSocial("PRESTAMIL DEL SUR SA DE CV");
        em.persist(empresa);
        Sucursal sucursal = new Sucursal();
        sucursal.setId(SUCURSAL);
        sucursal.setNumeroSucursal(7);
        sucursal.setNombre("Pinotepa 2");
        sucursal.setEmpresa(empresa);
        sucursal.setFechaRegistroProfeco(DIA_5_4.atStartOfDay());
        sucursal.setFechaContratoProfeco(DIA_5_4.atStartOfDay());
        em.persist(sucursal);

        semilla = new CorteCajaSemilla(em, configuracionRepository);
    }

    @Test
    void pdfDel17deSeptiembre_imprimeCadaCifraDeLaSeccion5_4() throws IOException {
        semilla.sembrar17deSeptiembre();

        CorteCajaReporteService.Archivo archivo = corteCajaReporteService.pdf(SUCURSAL, DEL_17, "cajero");

        assertThat(archivo.fecha()).isEqualTo(DIA_5_4);
        String texto = textoPdf(archivo.bytes());
        assertThat(texto)
                .contains("PRESTAMIL")
                .contains("PRESTAMIL DEL SUR SA DE CV")
                .contains("Sucursal: 7 Pinotepa 2")
                .contains("CORTE DE CAJA")
                .contains("CON FECHA DEL 17/09/2026")
                .contains("Impreso: 17/09/2026 18:42")
                .contains("RPT.DR-1.1.0");
        List<String> lineas = texto.lines().map(String::strip).toList();

        // Bloque superior en el orden de COCAE, con REPOSICIONES y sin renglones de cancelaciones (Q-C1)
        List<String> movimientos = bloque(lineas, "MOVIMIENTOS (EFECTIVO)", "COBRADO CON TARJETA");
        assertThat(movimientos.stream().map(CorteCajaReporteServiceIT::etiqueta).toList())
                .containsExactlyElementsOf(etiquetasMovimientos());
        assertCifras(movimientos, MOVIMIENTOS_5_4);
        assertThat(texto).doesNotContainIgnoringCase("cancela");

        // Tarjeta: informativa y aparte; el 17/09 no hubo cobros con tarjeta
        assertThat(bloque(lineas, "COBRADO CON TARJETA", "CAJA"))
                .containsExactly("Sin cobros con tarjeta en el día.", "No entra a la gaveta ni al arqueo.");

        // Bloque de caja en el orden pedido: saldo anterior … diferencia F/S y saldo final
        List<String> caja = bloque(lineas, "CAJA", "Diferencia:");
        assertThat(caja.stream().map(CorteCajaReporteServiceIT::etiqueta).toList())
                .containsExactlyElementsOf(CAJA_5_4.stream().map(Cifra::etiqueta).toList());
        assertCifras(caja, CAJA_5_4);
    }

    @Test
    void excelDel17deSeptiembre_traeCadaCifraComoNumeroEnSuColumna() throws IOException {
        semilla.sembrar17deSeptiembre();

        CorteCajaReporteService.Archivo archivo = corteCajaReporteService.xlsx(SUCURSAL, DEL_17, "cajero");

        List<List<String>> filas = filasXlsx(archivo.bytes());
        // Encabezado estándar completo: en Excel se pierde lo que se encima
        assertThat(filas).extracting(f -> f.get(0)).contains("PRESTAMIL", "PRESTAMIL DEL SUR SA DE CV",
                "Sucursal: 7 Pinotepa 2", "CORTE DE CAJA", "CON FECHA DEL 17/09/2026");
        List<List<String>> movimientos = bloqueXlsx(filas, "MOVIMIENTOS (EFECTIVO)", "COBRADO CON TARJETA (INFORMATIVO)");
        assertThat(movimientos).extracting(f -> f.get(0)).containsExactlyElementsOf(etiquetasMovimientos());
        assertCeldas(movimientos, MOVIMIENTOS_5_4);
        List<List<String>> caja = bloqueXlsx(filas, "CAJA", null);
        assertCeldas(caja.subList(0, CAJA_5_4.size()), CAJA_5_4);
    }

    @Test
    void sinSaldoDeArranque_pdfConEncabezadoYAvisoSinCifras() throws IOException {
        // Sin arranque capturado (Sistemas todavía no lo pone)
        CorteCajaReporteService.Archivo archivo = corteCajaReporteService.pdf(SUCURSAL, DEL_17, "cajero");

        String texto = textoPdf(archivo.bytes());
        assertThat(texto)
                .contains("CORTE DE CAJA")
                .contains("CON FECHA DEL 17/09/2026")
                .contains("Sin corte de caja para el 17/09/2026")
                .contains("no tiene saldo de arranque de caja")
                .doesNotContain("MOVIMIENTOS")
                .doesNotContain("TOTALES")
                .doesNotContain("SALDO FINAL");
        assertThat(texto).doesNotContainPattern("\\d,?\\d{3}\\.\\d{2}");
        // El Excel tampoco falla
        assertThat(new String(corteCajaReporteService.xlsx(SUCURSAL, DEL_17, "cajero").bytes(), 0, 2))
                .isEqualTo("PK");
    }

    @Test
    void antesDelArranque_pdfConElAvisoDeLaFechaDeArranque() throws IOException {
        semilla.arranque(DIA_5_4.plusDays(1), "41013.11");
        em.flush();

        String texto = textoPdf(corteCajaReporteService.pdf(SUCURSAL, DEL_17, "cajero").bytes());

        assertThat(texto)
                .contains("Sin corte de caja para el 17/09/2026")
                .contains("El corte de caja empieza el 18/09/2026")
                .doesNotContain("TOTALES");
    }

    @Test
    void cajeroPideOtroDia_recibeElDeHoy() {
        semilla.sembrar17deSeptiembre();

        CorteCajaReporteService.Archivo archivo = corteCajaReporteService.pdf(SUCURSAL,
                new ReporteFiltro(null, DIA_5_4.minusDays(1), null), "cajero");

        assertThat(archivo.fecha()).isEqualTo(DIA_5_4);
    }

    @Test
    void rangoORamo_400() {
        assertThatThrownBy(() -> corteCajaReporteService.pdf(SUCURSAL,
                new ReporteFiltro(null, DIA_5_4.minusDays(1), DIA_5_4), "cajero"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("un solo día");
        assertThatThrownBy(() -> corteCajaReporteService.xlsx(SUCURSAL, new ReporteFiltro(1, DIA_5_4, null), "cajero"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ramo");
    }

    // ---------------------------------------------------------------- PDF

    private static String textoPdf(byte[] pdf) throws IOException {
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        PdfReader reader = new PdfReader(pdf);
        try {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
            return new PdfTextExtractor(reader).getTextFromPage(1);
        } finally {
            reader.close();
        }
    }

    /** Líneas entre la que empieza con {@code desde} (sin incluirla) y la que empieza con {@code hasta}. */
    private static List<String> bloque(List<String> lineas, String desde, String hasta) {
        int inicio = indice(lineas, desde, 0) + 1;
        return lineas.subList(inicio, indice(lineas, hasta, inicio));
    }

    private static int indice(List<String> lineas, String prefijo, int desde) {
        for (int i = desde; i < lineas.size(); i++) {
            if (lineas.get(i).startsWith(prefijo)) {
                return i;
            }
        }
        throw new AssertionError("No está la línea \"" + prefijo + "\" en el PDF:\n" + String.join("\n", lineas));
    }

    private static final Pattern IMPORTE = Pattern.compile("-?[\\d,]+\\.\\d{2}");

    /** Etiqueta de una línea del PDF: lo que va antes del primer importe. */
    private static String etiqueta(String linea) {
        Matcher m = IMPORTE.matcher(linea);
        return (m.find() ? linea.substring(0, m.start()) : linea).strip();
    }

    /** En el PDF las columnas vacías no dejan rastro: se comparan los importes en orden. */
    private static void assertCifras(List<String> lineas, List<Cifra> esperadas) {
        assertThat(lineas).hasSameSizeAs(esperadas);
        for (int i = 0; i < esperadas.size(); i++) {
            Cifra esperada = esperadas.get(i);
            List<BigDecimal> impresos = new ArrayList<>();
            Matcher m = IMPORTE.matcher(lineas.get(i));
            while (m.find()) {
                impresos.add(new BigDecimal(m.group().replace(",", "")));
            }
            List<String> valores = Arrays.stream(new String[]{esperada.importe1(), esperada.importe2()})
                    .filter(v -> v != null)
                    .toList();
            assertThat(impresos).as(esperada.etiqueta()).hasSameSizeAs(valores);
            for (int j = 0; j < valores.size(); j++) {
                assertThat(impresos.get(j)).as(esperada.etiqueta())
                        .isCloseTo(new BigDecimal(valores.get(j)), within(COCAE));
            }
        }
    }

    private static List<String> etiquetasMovimientos() {
        List<String> etiquetas = new ArrayList<>(Arrays.stream(RenglonCorte.values())
                .map(RenglonCorte::getEtiqueta).toList());
        etiquetas.add("TOTALES");
        return etiquetas;
    }

    // ---------------------------------------------------------------- Excel

    private static final Pattern CELDA = Pattern.compile(
            "<c r=\"([A-Z]+)(\\d+)\"[^>]*?(?:/>|>(.*?)</c>)", Pattern.DOTALL);
    private static final Pattern TEXTO = Pattern.compile("<t[^>]*>(.*?)</t>", Pattern.DOTALL);
    private static final Pattern NUMERO = Pattern.compile("<v>(.*?)</v>");

    /**
     * Filas de la primera hoja: [concepto, importe 1, importe 2], con los importes como el número guardado
     * en la celda ("" si está vacía). Falla si un importe se guardó como texto.
     */
    private static List<List<String>> filasXlsx(byte[] xlsx) throws IOException {
        Map<String, String> partes = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                partes.put(e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        String hoja = partes.get("xl/worksheets/sheet1.xml");
        assertThat(hoja).as("hoja 1 del xlsx").isNotNull();

        // Por renglón de Excel, las celdas con contenido de izquierda a derecha
        Map<Integer, List<String[]>> renglones = new TreeMap<>();
        Matcher c = CELDA.matcher(hoja);
        while (c.find()) {
            String contenido = c.group(3);
            if (contenido == null) {
                continue;
            }
            Matcher t = TEXTO.matcher(contenido);
            Matcher v = NUMERO.matcher(contenido);
            String[] celda = t.find() ? new String[]{"t", desescapar(t.group(1))}
                    : v.find() ? new String[]{"n", v.group(1)} : null;
            if (celda != null && !celda[1].isBlank()) {
                renglones.computeIfAbsent(Integer.parseInt(c.group(2)), k -> new ArrayList<>()).add(
                        new String[]{c.group(1), celda[0], celda[1]});
            }
        }

        // La columna del concepto es la de la primera celda de texto; los importes, las dos siguientes
        List<List<String>> filas = new ArrayList<>();
        List<String> columnas = columnasImportes(renglones);
        for (List<String[]> celdas : renglones.values()) {
            List<String> fila = new ArrayList<>(List.of(celdas.get(0)[2], "", ""));
            for (String[] celda : celdas.subList(1, celdas.size())) {
                int i = columnas.indexOf(celda[0]);
                if (i >= 0 && "n".equals(celda[1])) {
                    fila.set(i + 1, celda[2]);
                }
            }
            filas.add(fila);
        }
        return filas;
    }

    /** Columnas de Excel de los encabezados "INGRESOS"/"EGRESOS" del primer bloque. */
    private static List<String> columnasImportes(Map<Integer, List<String[]>> renglones) {
        for (List<String[]> celdas : renglones.values()) {
            if (celdas.get(0)[2].equals("MOVIMIENTOS (EFECTIVO)")) {
                assertThat(celdas).extracting(c -> c[2]).contains("INGRESOS", "EGRESOS");
                return celdas.subList(1, 3).stream().map(c -> c[0]).toList();
            }
        }
        throw new AssertionError("El xlsx no tiene el bloque de movimientos");
    }

    private static List<List<String>> bloqueXlsx(List<List<String>> filas, String desde, String hasta) {
        int inicio = -1;
        int fin = filas.size();
        for (int i = 0; i < filas.size(); i++) {
            String concepto = filas.get(i).get(0);
            if (inicio < 0 && concepto.equals(desde)) {
                inicio = i + 1;
            } else if (inicio >= 0 && concepto.equals(hasta)) {
                fin = i;
                break;
            }
        }
        assertThat(inicio).as("bloque " + desde).isPositive();
        return filas.subList(inicio, fin);
    }

    private static void assertCeldas(List<List<String>> filas, List<Cifra> esperadas) {
        assertThat(filas).hasSameSizeAs(esperadas);
        for (int i = 0; i < esperadas.size(); i++) {
            Cifra esperada = esperadas.get(i);
            List<String> fila = filas.get(i);
            assertThat(fila.get(0)).isEqualTo(esperada.etiqueta());
            assertCelda(fila.get(1), esperada.importe1(), esperada.etiqueta());
            assertCelda(fila.get(2), esperada.importe2(), esperada.etiqueta());
        }
    }

    private static void assertCelda(String celda, String esperado, String etiqueta) {
        if (esperado == null) {
            assertThat(celda).as(etiqueta).isEmpty();
        } else {
            assertThat(new BigDecimal(celda)).as(etiqueta).isCloseTo(new BigDecimal(esperado), within(COCAE));
        }
    }

    private static String desescapar(String xml) {
        return xml.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&amp;", "&");
    }
}
