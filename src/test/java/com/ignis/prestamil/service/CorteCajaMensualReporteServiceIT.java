package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.exception.ForbiddenException;
import com.ignis.prestamil.model.CierreCajaDiario;
import com.ignis.prestamil.model.ConceptoCaja;
import com.ignis.prestamil.model.Empresa;
import com.ignis.prestamil.model.Rol;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.model.TipoTraspasoCaja;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.CierreCajaDiarioRepository;
import com.ignis.prestamil.repository.ConfiguracionRepository;
import com.ignis.prestamil.repository.TurnoRepository;
import com.ignis.prestamil.request.ReporteFiltro;
import com.ignis.prestamil.service.CorteCajaMensualReporteService.CorteCajaMensual;
import com.ignis.prestamil.service.CorteCajaMensualReporteService.Dia;
import com.ignis.prestamil.service.calculo.CorteCajaResultado;
import com.ignis.prestamil.service.calculo.CorteCajaSemilla;
import com.ignis.prestamil.service.calculo.CorteCajaService;
import com.ignis.prestamil.service.calculo.SaldoArranqueCajaProvider;
import com.ignis.prestamil.util.Constantes;
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
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.ignis.prestamil.service.calculo.CorteCajaSemilla.SUCURSAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Reporte mensual de corte de caja (REP-06) contra H2, con el motor del corte y el cierre de día reales. Hoy es
 * el 20/10/2026: septiembre es un mes pasado y octubre el mes en curso. Roles: 1 Sistemas, 3 Cajero, 5 Gerente.
 *
 * <p>TODO REP-05: cuando exista el resumen de operación diaria, agregar el test que compare los totales del
 * mensual (en especial los ingresos) contra los de REP-05 en el mismo rango, al centavo (RC-09).</p>
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CorteCajaMensualReporteService.class, CorteCajaService.class, ResolutorRenglonCorte.class,
        SaldoArranqueCajaProvider.class, CierreCajaService.class, CorteCajaMensualReporteServiceIT.Reloj.class})
class CorteCajaMensualReporteServiceIT {

    private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");
    private static final LocalDate HOY = LocalDate.of(2026, 10, 20);
    private static final BigDecimal COCAE = new BigDecimal("0.02");
    private static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final ReporteFiltro SEPTIEMBRE = mes(2026, 9);
    private static final ReporteFiltro OCTUBRE = mes(2026, 10);

    @TestConfiguration
    static class Reloj {
        @Bean
        Clock clock() {
            return Clock.fixed(HOY.atTime(18, 0).atZone(ZONA).toInstant(), ZONA);
        }
    }

    @Autowired CorteCajaMensualReporteService service;
    @Autowired CierreCajaService cierreCajaService;
    @Autowired CierreCajaDiarioRepository cierreCajaDiarioRepository;
    @Autowired ConfiguracionRepository configuracionRepository;
    @Autowired TurnoRepository turnoRepository;
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
        sucursal.setFechaRegistroProfeco(HOY.atStartOfDay());
        sucursal.setFechaContratoProfeco(HOY.atStartOfDay());
        em.persist(sucursal);

        semilla = new CorteCajaSemilla(em, configuracionRepository);
        // Cerrar un día exige que no haya turno abierto
        turnoRepository.findByActivo(true).orElseThrow().setActivo(false);
        usuario("gerente", rol(5, "Gerente"));
        usuario("sistemas", rol(1, "Sistemas"));
        semilla.configuracion(Constantes.ROLES_PERMITIDOS_CORTE_CAJA_MENSUAL, "5,1");
        semilla.configuracion(Constantes.ROLES_PERMITIDOS_CIERRE_CAJA, "5,1");
        semilla.configuracion(Constantes.ROLES_PERMITIDOS_CIERRE_CAJA_PENDIENTE, "1");
    }

    // ---------------------------------------------------------------- 5.4

    /**
     * Fila 01/09 del mensual de la sección 5.4: bóveda 75,015.01 − 21,521.00 = 53,494.01 y diferencia
     * 5,863.86 + 146,664.84 − 26,590.00 − 125,938.69 = 0.01 F.
     */
    @Test
    void filaDel01deSeptiembre_cuadraConLaSeccion5_4() throws IOException {
        sembrar01deSeptiembre();

        CorteCajaMensual mensual = service.mensual(SUCURSAL, SEPTIEMBRE, "gerente");

        Dia primero = mensual.dias().get(0);
        assertThat(primero.fecha()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertCerca(primero.saldoInicial(), "75015.01");
        assertCerca(primero.ministraciones(), "0.00");
        assertCerca(primero.concentraciones(), "21521.00");
        assertCerca(primero.boveda(), "53494.01");
        assertCerca(primero.dotaciones(), "5863.86");
        assertCerca(primero.ingresos(), "146664.84");
        assertCerca(primero.egresos(), "26590.00");
        assertCerca(primero.recolecciones(), "125938.69");
        assertCerca(primero.diferencia(), "0.01");
        assertThat(primero.indicador()).isEqualTo(CorteCajaResultado.Indicador.F);
        assertCerca(primero.saldoFinal(), "173568.84");
        // Nadie cerró el 01/09 y ya pasó
        assertThat(primero.pendiente()).isTrue();

        // Los 29 días sin movimientos sí aparecen, con el saldo arrastrado y sin marca de pendiente
        assertThat(mensual.dias()).hasSize(30);
        for (Dia dia : mensual.dias().subList(1, 30)) {
            assertThat(dia.saldoInicial()).as(dia.fecha().toString()).isEqualByComparingTo("173568.84");
            assertThat(dia.saldoFinal()).as(dia.fecha().toString()).isEqualByComparingTo("173568.84");
            assertThat(dia.diferencia()).as(dia.fecha().toString()).isEqualByComparingTo("0.00");
            assertThat(dia.pendiente()).as(dia.fecha().toString()).isFalse();
        }
        assertThat(mensual.totales().diferencia()).isEqualByComparingTo("0.01");
        assertThat(mensual.totales().indicador()).isEqualTo("FALTANTE");

        // PDF: encabezado estándar y la fila tal cual
        String texto = textoPdf(service.pdf(SUCURSAL, SEPTIEMBRE, "gerente").bytes());
        assertThat(texto)
                .contains("PRESTAMIL DEL SUR SA DE CV")
                .contains("Sucursal: 7 Pinotepa 2")
                .contains("REPORTE MENSUAL DE CORTE DE CAJA")
                .contains("CON FECHA DEL 01/09/2026 AL 30/09/2026")
                .contains("ROD-1.0");
        assertThat(linea(texto, "01/09/2026"))
                .isEqualTo("01/09/2026 75,015.01 0.00 21,521.00 53,494.01 5,863.86 146,664.84 26,590.00 "
                        + "125,938.69 0.01 F 173,568.84 PENDIENTE");
        assertThat(linea(texto, "Totales"))
                .isEqualTo("Totales 0.00 21,521.00 5,863.86 146,664.84 26,590.00 125,938.69 0.01 FALTANTE");

        // Excel: la misma fila, con los importes como números
        Map<String, List<String>> filas = filasXlsx(service.xlsx(SUCURSAL, SEPTIEMBRE, "sistemas").bytes());
        assertThat(filas.get("01/09/2026")).containsExactly("75015.01", "0", "21521", "53494.01", "5863.86",
                "146664.84", "26590", "125938.69", "0.01", "F", "173568.84", "PENDIENTE");
        assertThat(filas.get("30/09/2026")).first().isEqualTo("173568.84");
        assertThat(filas.get("Totales")).containsExactly("0", "21521", "5863.86", "146664.84", "26590",
                "125938.69", "0.01", "FALTANTE");
    }

    // ---------------------------------------------------------------- Arrastre

    /**
     * Octubre hasta hoy (20/10) con días cerrados (01 y 03), sin movimientos (02, 04, 05…), pendientes (06 y
     * 10) y abierto (hoy). El saldo final de cada día es el saldo inicial del siguiente en todo el mes.
     */
    @Test
    void arrastreDeSaldo_todoElMes_conDiasCerradosAbiertosPendientesYSinMovimientos() throws IOException {
        semilla.arranque(dia(1), "10000.00");
        // 01/10: diferencia 3,000 − 1,000 + 500 − 2,500 = 0
        semilla.traspaso(TipoTraspasoCaja.DOTACION, "3000.00", dia(1));
        semilla.flujo(ConceptoCaja.EMPENO, "1000.00", "0.00", null, null, dia(1).atTime(10, 0));
        semilla.flujo(ConceptoCaja.REFRENDO, "500.00", "0.00", null, null, dia(1).atTime(11, 0));
        semilla.traspaso(TipoTraspasoCaja.RECOLECCION, "2500.00", dia(1));
        // 03/10: ministración y diferencia 2,000 + 800 − 2,700 = 100 F
        semilla.traspaso(TipoTraspasoCaja.MINISTRACION, "5000.00", dia(3));
        semilla.traspaso(TipoTraspasoCaja.DOTACION, "2000.00", dia(3));
        semilla.flujo(ConceptoCaja.FINIQUITO, "800.00", "0.00", null, null, dia(3).atTime(12, 0));
        semilla.traspaso(TipoTraspasoCaja.RECOLECCION, "2700.00", dia(3));
        // 06/10: concentración y diferencia 1,000 − 600 − 300 = 100 F; nadie lo cierra
        semilla.traspaso(TipoTraspasoCaja.CONCENTRACION, "4000.00", dia(6));
        semilla.traspaso(TipoTraspasoCaja.DOTACION, "1000.00", dia(6));
        semilla.flujo(ConceptoCaja.EMPENO, "600.00", "0.00", null, null, dia(6).atTime(10, 0));
        semilla.traspaso(TipoTraspasoCaja.RECOLECCION, "300.00", dia(6));
        // 10/10: diferencia 500 + 200 − 750 = −50 S; nadie lo cierra
        semilla.traspaso(TipoTraspasoCaja.DOTACION, "500.00", dia(10));
        semilla.flujo(ConceptoCaja.REFRENDO, "200.00", "0.00", null, null, dia(10).atTime(10, 0));
        semilla.traspaso(TipoTraspasoCaja.RECOLECCION, "750.00", dia(10));
        // Hoy: día abierto, diferencia 1,000 − 200 = 800 F
        semilla.traspaso(TipoTraspasoCaja.DOTACION, "1000.00", HOY);
        semilla.flujo(ConceptoCaja.EMPENO, "200.00", "0.00", null, null, HOY.atTime(10, 0));
        em.flush();

        // Sistemas cierra el 01 y el 03 (ya pasaron); el 02 no tuvo movimientos y no se cierra
        cierreCajaService.cerrar(SUCURSAL, dia(1), "sistemas");
        cierreCajaService.cerrar(SUCURSAL, dia(3), "sistemas");
        // Un flujo que se cuela en el 03/10 ya cerrado no cambia el mensual: el día sale congelado
        semilla.flujo(ConceptoCaja.FINIQUITO, "999.00", "0.00", null, null, dia(3).atTime(17, 0));
        em.flush();

        CorteCajaMensual mensual = service.mensual(SUCURSAL, OCTUBRE, "gerente");
        List<Dia> dias = mensual.dias();

        // Del 01 a hoy, un renglón por día; el primero parte del arranque
        assertThat(dias).extracting(Dia::fecha)
                .containsExactlyElementsOf(dia(1).datesUntil(HOY.plusDays(1)).toList());
        assertThat(dias.get(0).saldoInicial()).isEqualByComparingTo("10000.00");
        // El arrastre: el saldo final de cada día es el saldo inicial del siguiente, todo el mes
        for (int i = 1; i < dias.size(); i++) {
            assertThat(dias.get(i).saldoInicial()).as("saldo inicial del " + dias.get(i).fecha())
                    .isEqualByComparingTo(dias.get(i - 1).saldoFinal());
        }
        // RC-08 en cada día
        for (Dia d : dias) {
            assertThat(d.boveda()).as("bóveda del " + d.fecha())
                    .isEqualByComparingTo(d.saldoInicial().add(d.ministraciones()).subtract(d.concentraciones()));
            assertThat(d.saldoFinal()).as("saldo final del " + d.fecha())
                    .isEqualByComparingTo(d.boveda().subtract(d.dotaciones()).add(d.recolecciones()));
        }

        // Cerrados: idénticos a su cierre congelado (el flujo colado de 999 no aparece)
        for (int d : new int[]{1, 3}) {
            CierreCajaDiario cierre = cierreCajaDiarioRepository.findBySucursalIdAndFecha(SUCURSAL, dia(d)).orElseThrow();
            Dia renglon = renglon(dias, dia(d));
            assertThat(renglon.saldoFinal()).isEqualByComparingTo(cierre.getSaldoFinal());
            assertThat(renglon.ingresos()).isEqualByComparingTo(cierre.getIngresosEfectivo());
            assertThat(renglon.diferencia()).isEqualByComparingTo(cierre.getDiferencia());
            assertThat(renglon.pendiente()).isFalse();
        }
        assertThat(renglon(dias, dia(3)).ingresos()).isEqualByComparingTo("800.00");
        assertThat(renglon(dias, dia(1)).saldoFinal()).isEqualByComparingTo("9500.00");
        assertThat(renglon(dias, dia(3)).saldoFinal()).isEqualByComparingTo("15200.00");

        // Sin movimientos: aparecen con el saldo arrastrado
        Dia dos = renglon(dias, dia(2));
        assertThat(dos.saldoInicial()).isEqualByComparingTo("9500.00");
        assertThat(dos.saldoFinal()).isEqualByComparingTo("9500.00");
        assertThat(dos.ingresos()).isEqualByComparingTo("0.00");
        assertThat(dos.pendiente()).isFalse();

        // Pendientes: los que ya pasaron con movimientos y sin cierre; hoy sigue abierto
        assertThat(dias.stream().filter(Dia::pendiente).map(Dia::fecha)).containsExactly(dia(6), dia(10));
        assertThat(renglon(dias, dia(6)).saldoFinal()).isEqualByComparingTo("10500.00");
        assertThat(renglon(dias, dia(10)).indicador()).isEqualTo(CorteCajaResultado.Indicador.S);
        assertThat(renglon(dias, HOY).pendiente()).isFalse();
        assertThat(dias.get(dias.size() - 1).saldoFinal()).isEqualByComparingTo("9750.00");

        // Totales: cada columna sumada, y la diferencia total es la suma de las diarias
        assertThat(mensual.totales().diferencia())
                .isEqualByComparingTo(suma(dias, Dia::diferencia))
                .isEqualByComparingTo("950.00");
        assertThat(mensual.totales().indicador()).isEqualTo("FALTANTE");
        assertThat(mensual.totales().ingresos()).isEqualByComparingTo("1500.00");
        assertThat(mensual.totales().egresos()).isEqualByComparingTo("1800.00");
        assertThat(mensual.totales().dotaciones()).isEqualByComparingTo("7500.00");
        assertThat(mensual.totales().recolecciones()).isEqualByComparingTo("6250.00");
        assertThat(mensual.totales().ministraciones()).isEqualByComparingTo("5000.00");
        assertThat(mensual.totales().concentraciones()).isEqualByComparingTo("4000.00");

        // En el PDF: la marca de pendiente y la aclaración al pie
        String texto = textoPdf(service.pdf(SUCURSAL, OCTUBRE, "gerente").bytes());
        assertThat(linea(texto, "06/10/2026")).endsWith("10,500.00 PENDIENTE");
        assertThat(linea(texto, "03/10/2026")).doesNotContain("PENDIENTE");
        assertThat(linea(texto, "20/10/2026")).doesNotContain("PENDIENTE");
        assertThat(texto).contains("PENDIENTE: el día tuvo movimientos y no se ha cerrado");
    }

    // ---------------------------------------------------------------- Días especiales

    @Test
    void mesCompletoAntesDelArranque_encabezadoConAvisoSinCifras() throws IOException {
        semilla.arranque(dia(1), "10000.00");
        em.flush();

        CorteCajaMensual mensual = service.mensual(SUCURSAL, SEPTIEMBRE, "gerente");
        assertThat(mensual.valido()).isFalse();
        assertThat(mensual.dias()).isEmpty();
        assertThat(mensual.totales()).isNull();

        String texto = textoPdf(service.pdf(SUCURSAL, SEPTIEMBRE, "gerente").bytes());
        assertThat(texto)
                .contains("REPORTE MENSUAL DE CORTE DE CAJA")
                .contains("CON FECHA DEL 01/09/2026 AL 30/09/2026")
                .contains("Sin corte de caja de septiembre de 2026")
                .contains("El corte de caja empieza el 01/10/2026")
                .doesNotContain("Saldo inicial")
                .doesNotContain("Totales");
        assertThat(texto).doesNotContainPattern("\\d,?\\d{3}\\.\\d{2}");
        // El Excel tampoco falla
        assertThat(new String(service.xlsx(SUCURSAL, SEPTIEMBRE, "gerente").bytes(), 0, 2)).isEqualTo("PK");
    }

    @Test
    void sinSaldoDeArranque_avisoSinCifras() throws IOException {
        String texto = textoPdf(service.pdf(SUCURSAL, OCTUBRE, "gerente").bytes());

        assertThat(texto)
                .contains("Sin corte de caja de octubre de 2026")
                .contains("no tiene saldo de arranque de caja")
                .doesNotContain("Totales");
    }

    @Test
    void mesEnCurso_llegaHastaHoy_yEmpiezaEnElArranque() throws IOException {
        semilla.arranque(dia(15), "5000.00");
        semilla.traspaso(TipoTraspasoCaja.DOTACION, "1000.00", HOY);
        em.flush();

        CorteCajaMensual mensual = service.mensual(SUCURSAL, OCTUBRE, "gerente");

        // Del arranque (15/10) a hoy (20/10): ni los días antes del arranque ni los que faltan del mes
        assertThat(mensual.hasta()).isEqualTo(HOY);
        assertThat(mensual.dias()).extracting(Dia::fecha)
                .containsExactlyElementsOf(dia(15).datesUntil(HOY.plusDays(1)).toList());
        assertThat(mensual.dias().get(0).saldoInicial()).isEqualByComparingTo("5000.00");
        assertThat(mensual.notas())
                .anyMatch(n -> n.startsWith("El corte de caja empieza el 15/10/2026"))
                .anyMatch(n -> n.startsWith("Mes en curso: llega hasta hoy, 20/10/2026"));

        // Sin filtro de fechas también es el mes en curso
        assertThat(service.mensual(SUCURSAL, new ReporteFiltro(null, null, null), "gerente").dias())
                .hasSameSizeAs(mensual.dias());

        // El encabezado dice el mes completo, como COCAE
        String texto = textoPdf(service.pdf(SUCURSAL, OCTUBRE, "gerente").bytes());
        assertThat(texto).contains("CON FECHA DEL 01/10/2026 AL 31/10/2026");
        assertThat(texto).doesNotContain("14/10/2026").doesNotContain("21/10/2026");
        assertThat(linea(texto, "20/10/2026")).contains("1,000.00");
    }

    // ---------------------------------------------------------------- Filtros y permisos

    @Test
    void ramoRangoQueNoEsUnMesOMesFuturo_400() {
        assertThatThrownBy(() -> service.pdf(SUCURSAL, new ReporteFiltro(1, dia(1), dia(31)), "gerente"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ramo");
        assertThatThrownBy(() -> service.pdf(SUCURSAL, new ReporteFiltro(null, dia(1), dia(15)), "gerente"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("mes completo");
        assertThatThrownBy(() -> service.xlsx(SUCURSAL, new ReporteFiltro(null, dia(2), null), "gerente"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("mes completo");
        assertThatThrownBy(() -> service.pdf(SUCURSAL, new ReporteFiltro(null, LocalDate.of(2026, 9, 1), dia(31)), "gerente"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("mes completo");
        assertThatThrownBy(() -> service.pdf(SUCURSAL, mes(2026, 11), "gerente"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("mes futuro");
    }

    @Test
    void cajero_noLoVe() {
        semilla.arranque(dia(1), "10000.00");
        em.flush();

        assertThatThrownBy(() -> service.pdf(SUCURSAL, OCTUBRE, "cajero")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.xlsx(SUCURSAL, OCTUBRE, "cajero")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.contexto(SUCURSAL, "cajero")).isInstanceOf(ForbiddenException.class);
        // Gerente y Sistemas sí
        assertThat(service.contexto(SUCURSAL, "gerente").fechaArranque()).isEqualTo(dia(1));
        assertThat(service.contexto(SUCURSAL, "sistemas").hoy()).isEqualTo(HOY);
    }

    // ---------------------------------------------------------------- Ayudas

    /** Movimientos del 01/09/2026 de la fila 01/09 del mensual de 5.4. */
    private void sembrar01deSeptiembre() {
        LocalDate primero = LocalDate.of(2026, 9, 1);
        semilla.arranque(primero, "75015.01");
        semilla.traspaso(TipoTraspasoCaja.CONCENTRACION, "21521.00", primero);
        semilla.traspaso(TipoTraspasoCaja.DOTACION, "5863.86", primero);
        semilla.flujo(ConceptoCaja.FINIQUITO, "90000.00", "0.00", null, null, primero.atTime(10, 0));
        semilla.flujo(ConceptoCaja.REFRENDO, "56664.84", "0.00", null, null, primero.atTime(11, 0));
        semilla.flujo(ConceptoCaja.EMPENO, "26590.00", "0.00", null, null, primero.atTime(12, 0));
        semilla.traspaso(TipoTraspasoCaja.RECOLECCION, "125938.69", primero);
        em.flush();
    }

    private static ReporteFiltro mes(int anio, int mes) {
        YearMonth ym = YearMonth.of(anio, mes);
        return new ReporteFiltro(null, ym.atDay(1), ym.atEndOfMonth());
    }

    private static LocalDate dia(int dia) {
        return LocalDate.of(2026, 10, dia);
    }

    private static Dia renglon(List<Dia> dias, LocalDate fecha) {
        return dias.stream().filter(d -> d.fecha().equals(fecha)).findFirst().orElseThrow();
    }

    private static BigDecimal suma(List<Dia> dias, Function<Dia, BigDecimal> columna) {
        return dias.stream().map(columna).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static void assertCerca(BigDecimal valor, String esperado) {
        assertThat(valor).isCloseTo(new BigDecimal(esperado), within(COCAE));
    }

    private Rol rol(int id, String nombre) {
        em.createNativeQuery("INSERT INTO roles (id, rol, estatus) VALUES (?1, ?2, TRUE)")
                .setParameter(1, id)
                .setParameter(2, nombre)
                .executeUpdate();
        return em.find(Rol.class, id);
    }

    private void usuario(String nombreUsuario, Rol rol) {
        Usuario u = new Usuario();
        u.setNombreUsuario(nombreUsuario);
        u.setPassword("x");
        u.setNombre(nombreUsuario);
        u.setApellidos("Prueba");
        u.setEstatus(true);
        u.setCreado(HOY.atStartOfDay());
        u.setRol(rol);
        u.setVigencia(true);
        u.setFechaCambioPass(HOY);
        em.persist(u);
    }

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

    /** La línea del PDF que empieza con {@code prefijo}, con los espacios normalizados. */
    private static String linea(String texto, String prefijo) {
        return texto.lines()
                .map(l -> l.strip().replaceAll("\\s+", " "))
                .filter(l -> l.startsWith(prefijo))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No está la línea \"" + prefijo + "\" en el PDF:\n" + texto));
    }

    private static final Pattern CELDA = Pattern.compile(
            "<c r=\"([A-Z]+)(\\d+)\"[^>]*?(?:/>|>(.*?)</c>)", Pattern.DOTALL);
    private static final Pattern TEXTO = Pattern.compile("<t[^>]*>(.*?)</t>", Pattern.DOTALL);
    private static final Pattern NUMERO = Pattern.compile("<v>(.*?)</v>");

    /**
     * Renglones de la primera hoja por el texto de su primera celda: el resto de sus celdas con contenido, de
     * izquierda a derecha. Los importes van como el número guardado (no como texto), sin ceros de sobra.
     */
    private static Map<String, List<String>> filasXlsx(byte[] xlsx) throws IOException {
        String hoja = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                if (e.getName().equals("xl/worksheets/sheet1.xml")) {
                    hoja = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        assertThat(hoja).as("hoja 1 del xlsx").isNotNull();

        Map<Integer, List<String[]>> renglones = new TreeMap<>();
        Matcher c = CELDA.matcher(hoja);
        while (c.find()) {
            String contenido = c.group(3);
            if (contenido == null) {
                continue;
            }
            Matcher t = TEXTO.matcher(contenido);
            Matcher v = NUMERO.matcher(contenido);
            String[] celda = t.find() ? new String[]{"t", t.group(1)} : v.find() ? new String[]{"n", v.group(1)} : null;
            if (celda != null && !celda[1].isBlank()) {
                renglones.computeIfAbsent(Integer.parseInt(c.group(2)), k -> new ArrayList<>()).add(celda);
            }
        }
        Map<String, List<String>> filas = new TreeMap<>();
        for (List<String[]> celdas : renglones.values()) {
            List<String> resto = new ArrayList<>();
            for (String[] celda : celdas.subList(1, celdas.size())) {
                // Números sin ceros de sobra: 21521.00 y 21521 son la misma celda
                resto.add("n".equals(celda[0]) ? new BigDecimal(celda[1]).stripTrailingZeros().toPlainString()
                        : celda[1]);
            }
            filas.put(celdas.get(0)[1], resto);
        }
        return filas;
    }
}
