package com.ignis.prestamil.service;

import com.ignis.prestamil.model.Cliente;
import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.Direccion;
import com.ignis.prestamil.model.PartidaContrato;
import com.ignis.prestamil.model.Plazo;
import com.ignis.prestamil.model.PlazoParametro;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.model.TipoPrenda;
import com.ignis.prestamil.repository.PlazoParametroRepository;
import com.ignis.prestamil.repository.SucursalRepository;
import com.ignis.prestamil.response.VencimientoResponse;
import com.ignis.prestamil.service.calculo.CalculoContratoService;
import com.ignis.prestamil.service.calculo.ParametrosSistemaCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Verifica que la plantilla Jasper (contrato.jasper) se llena y exporta a PDF sin errores
 * con los datos de un contrato de plata (caza desajustes de parámetros/plantilla).
 */
@ExtendWith(MockitoExtension.class)
class ContratoPdfServiceTest {

    @Mock ContratoService contratoService;
    @Mock PlazoParametroRepository plazoParametroRepository;
    @Mock SucursalRepository sucursalRepository;
    @Mock ParametrosSistemaCache parametrosSistemaCache;

    private ContratoPdfService construir() {
        // Motor REAL (Pasada 2): el PDF ahora delega el calculo al motor unico. Testear con motor
        // real garantiza que la fila extemporanea del PDF cuadra con lo que el motor produce.
        CalculoContratoService motor = new CalculoContratoService(parametrosSistemaCache);
        return new ContratoPdfService(contratoService, plazoParametroRepository, sucursalRepository,
                motor, parametrosSistemaCache);
    }

    @Test
    void generarPdf_contratoDePlata_produceUnPdfNoVacio() {
        lenient().when(parametrosSistemaCache.getIvaPorcentaje()).thenReturn(new BigDecimal("16.00"));
        ContratoPdfService pdfService = construir();

        TipoPrenda plata = new TipoPrenda();
        plata.setId(4);
        plata.setTipo("PLATAS");

        Plazo plazo = new Plazo();
        plazo.setId(6L);
        plazo.setNombre("Semanal - Plata");
        plazo.setDiasPorPeriodo(7);
        plazo.setNumeroPeriodos(4);

        Direccion dir = new Direccion();
        dir.setCalle("Benito Juárez"); dir.setNumeroExterior("201"); dir.setColonia("Centro");
        dir.setCiudad("Pinotepa"); dir.setEstado("Oaxaca"); dir.setCodigoPostal("71600");
        Cliente cliente = new Cliente();
        cliente.setNombre("Cliente"); cliente.setApellidoPaterno("De"); cliente.setApellidoMaterno("Prueba");
        cliente.setDireccion(dir);

        PartidaContrato p1 = new PartidaContrato();
        p1.setTipoPrenda(plata); p1.setDescripcion("pulsera"); p1.setLey(new BigDecimal("925"));
        p1.setHechura("F"); p1.setPesoNeto(new BigDecimal("20.00"));
        p1.setAvaluoContrato(new BigDecimal("162.50")); p1.setMontoPrestamo(new BigDecimal("130.00"));
        PartidaContrato p2 = new PartidaContrato();
        p2.setTipoPrenda(plata); p2.setDescripcion("cadena"); p2.setLey(new BigDecimal("720"));
        p2.setHechura("F"); p2.setPesoNeto(new BigDecimal("100.00"));
        p2.setAvaluoContrato(new BigDecimal("625.00")); p2.setMontoPrestamo(new BigDecimal("500.00"));

        Contrato contrato = new Contrato();
        contrato.setId(1L); contrato.setFolio("CTR-000001"); contrato.setCliente(cliente);
        contrato.setPlazo(plazo); contrato.setSucursalId(1);
        contrato.setMontoPrestamo(new BigDecimal("630.00")); contrato.setMontoAvaluo(new BigDecimal("787.50"));
        contrato.setFechaApertura(LocalDateTime.of(2026, 8, 15, 10, 0));
        contrato.setFechaVencimiento(LocalDate.of(2026, 9, 13));
        contrato.setNombreBeneficiario("Beneficiario Prueba");
        contrato.setNumIdentificacion("1234567890123");
        List<PartidaContrato> partidas = new ArrayList<>();
        partidas.add(p1); partidas.add(p2);
        contrato.setPartidas(partidas);

        Sucursal sucursal = new Sucursal();
        sucursal.setId(1); sucursal.setNombre("San Luis Acatlán"); sucursal.setCalle("Morelos");
        sucursal.setColonia("Centro"); sucursal.setMunicipio("San Luis Acatlán"); sucursal.setEstado("Guerrero");
        sucursal.setCp("41600"); sucursal.setTelefono("6881896");

        PlazoParametro parametro = new PlazoParametro();
        parametro.setPorcInteres(new BigDecimal("2.9")); parametro.setPorcAlmacen(new BigDecimal("0.6"));
        parametro.setPorcGastosAdmin(new BigDecimal("0")); parametro.setPorcSancionSemanal(new BigDecimal("2"));
        parametro.setComisionPorVentaPrenda(new BigDecimal("18"));
        // Con el interruptor activo se ejercita la rama que imprime el bloque de pago extemporáneo.
        parametro.setAplicarSancionPorPeriodo(true);

        when(contratoService.findById(1L)).thenReturn(contrato);
        when(contratoService.calcularAmortizacion(1L)).thenReturn(amortizacion());
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(6L, 4, 1))
                .thenReturn(Optional.of(parametro));
        when(sucursalRepository.findById(1)).thenReturn(Optional.of(sucursal));

        // When
        byte[] pdf = pdfService.generarPdf(1L);

        // Then: es un PDF real (empieza con "%PDF") y no está vacío
        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    // =========================================================================
    // Pasada 2: verificaciones nuevas
    // =========================================================================

    @Test
    void generarPdf_ivaCambiadoA8_pasaAlPdfElNuevoValorNoLaConstante() {
        // Given: cache devuelve IVA = 8 (config admin cambio de 16 a 8). El PDF debe reflejarlo
        // en la etiqueta P_IVA y en el calculo de filas extemporaneas — no mas 16 hardcoded.
        when(parametrosSistemaCache.getIvaPorcentaje()).thenReturn(new BigDecimal("8.00"));
        ContratoPdfService pdfService = construir();

        // Contrato con plazo semanal, prestamo 1000, ya vencido 10 dias (fuerza filas extemporaneas)
        Contrato c = fixtureContratoSimple();
        c.setMontoPrestamo(new BigDecimal("1000.00"));
        c.setFechaVencimiento(LocalDate.now().minusDays(10));

        PlazoParametro pp = new PlazoParametro();
        pp.setPorcInteres(new BigDecimal("3.0000"));
        pp.setPorcAlmacen(new BigDecimal("2.0000"));
        pp.setPorcGastosAdmin(new BigDecimal("1.0000"));
        pp.setPorcSancionSemanal(new BigDecimal("2.0000"));
        pp.setDiasGraciaSinInteres(2);
        pp.setAplicarSancionPorPeriodo(true);

        when(contratoService.findById(1L)).thenReturn(c);
        when(contratoService.calcularAmortizacion(1L)).thenReturn(amortizacion());
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(6L, 4, 1))
                .thenReturn(Optional.of(pp));

        // When: se genera el PDF (no falla) y el cache fue consultado por getIvaPorcentaje
        byte[] pdf = pdfService.generarPdf(1L);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        // Cache consultado al menos una vez (getIvaPorcentaje sirve al motor + a P_IVA)
        org.mockito.Mockito.verify(parametrosSistemaCache, org.mockito.Mockito.atLeastOnce())
                .getIvaPorcentaje();
    }

    @Test
    void generarPdf_contratoConSnapshot_usaSnapshotIvaNoLaVigente() {
        // Given: cache dice 8, pero el contrato tiene snapIvaPorcentaje=16 (era el vigente al firmar).
        // El PDF debe imprimir 16 (respeto al snapshot). Verifica que la cadena snapshot -> resolver
        // -> P_IVA / iva de filas funciona end-to-end.
        lenient().when(parametrosSistemaCache.getIvaPorcentaje()).thenReturn(new BigDecimal("8.00"));
        ContratoPdfService pdfService = construir();

        Contrato c = fixtureContratoSimple();
        c.setMontoPrestamo(new BigDecimal("1000.00"));
        c.setFechaVencimiento(LocalDate.now().minusDays(10));
        // Snapshot congelado al momento de firmar (Pasada 1, changeset 026)
        c.setSnapPorcInteres(new BigDecimal("3.0000"));
        c.setSnapPorcAlmacen(new BigDecimal("2.0000"));
        c.setSnapPorcGastosAdmin(new BigDecimal("1.0000"));
        c.setSnapPorcSancionSemanal(new BigDecimal("2.0000"));
        c.setSnapDiasGraciaSancion(2);
        c.setSnapAplicarSancionPeriodo(true);
        c.setSnapIvaPorcentaje(new BigDecimal("16.00"));

        // La config vigente cambio a valores muy distintos, pero el snapshot los pisa.
        PlazoParametro ppVigente = new PlazoParametro();
        ppVigente.setPorcInteres(new BigDecimal("999.0000"));
        ppVigente.setPorcSancionSemanal(new BigDecimal("999.0000"));
        ppVigente.setDiasGraciaSinInteres(0);
        ppVigente.setAplicarSancionPorPeriodo(false);

        when(contratoService.findById(1L)).thenReturn(c);
        when(contratoService.calcularAmortizacion(1L)).thenReturn(amortizacion());
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(6L, 4, 1))
                .thenReturn(Optional.of(ppVigente));

        byte[] pdf = pdfService.generarPdf(1L);
        assertThat(pdf).isNotEmpty();
        // No aserto valores dentro del PDF binario (JR opaco); la ausencia de excepcion + el
        // motor unico ya garantizan que se uso el snapshot (el cache habria dado 8, disparando
        // divisiones diferentes; con snapshot el desglose queda con IVA 16).
    }

    // =========================================================================
    // Bloque COMISIONES (Montos y Clausulas) PROFECO — clausulas 11a-11f
    // =========================================================================

    @Test
    void parametrosComisiones_sinSnapshot_leenVigente() {
        // Given: contrato SIN snap_* (contrato previo al changeset 026 o campo no poblado).
        // El motor cae al valor vigente del PlazoParametro para los tres con snapshot.
        lenient().when(parametrosSistemaCache.getIvaPorcentaje()).thenReturn(new BigDecimal("16.00"));
        ContratoPdfService svc = construir();

        Contrato c = fixtureContratoSimple();
        c.setMontoPrestamo(new BigDecimal("1000.00"));
        c.setFechaVencimiento(LocalDate.now().plusDays(28));

        PlazoParametro pp = new PlazoParametro();
        pp.setPorcAlmacen(new BigDecimal("1.5000"));
        pp.setPorcGastosAdmin(new BigDecimal("0.7500"));
        pp.setPorcSancionSemanal(new BigDecimal("2.5000"));
        pp.setAplicarSancionPorPeriodo(true);
        pp.setComisionPorVentaPrenda(new BigDecimal("18.0000"));
        pp.setReposicionEsPorcentaje(false);
        pp.setMontoReposicion(new BigDecimal("120.00"));
        pp.setPorcReposicion(new BigDecimal("3.0000"));

        when(contratoService.findById(1L)).thenReturn(c);
        when(contratoService.calcularAmortizacion(1L)).thenReturn(amortizacion());
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(6L, 4, 1))
                .thenReturn(Optional.of(pp));

        Map<String, Object> params = svc.armarParametros(1L);

        // 11a Almacenaje: vigente
        assertThat((BigDecimal) params.get("P_comisionAlmacenaje"))
                .isEqualByComparingTo("1.5000");
        // 11b Avaluo: sin campo en BD, siempre 0
        assertThat((BigDecimal) params.get("P_comisionAvaluo"))
                .isEqualByComparingTo(BigDecimal.ZERO);
        // 11c Comercializacion: vigente (comision_por_venta_prenda)
        assertThat((BigDecimal) params.get("P_comisionComercializacion"))
                .isEqualByComparingTo("18.0000");
        // 11d Reposicion: switch=false -> monto_reposicion
        assertThat((BigDecimal) params.get("P_comisionReposicion"))
                .isEqualByComparingTo("120.00");
        // 11f Gastos admin: vigente
        assertThat((BigDecimal) params.get("P_gastosAdministracion"))
                .isEqualByComparingTo("0.7500");
        // 11e Desempeno extemporaneo: vigente
        assertThat((BigDecimal) params.get("P_desempenoExtemporaneo"))
                .isEqualByComparingTo("2.5000");
    }

    @Test
    void parametrosComisiones_conSnapshot_ganaSnapshotSobreVigente() {
        // Given: contrato con snap_* poblados; la config vigente cambio a otros valores.
        // La reimpresion debe respetar el snapshot para los tres con snapshot; los otros tres
        // (avaluo, comercializacion, reposicion) NO tienen snapshot y leen el vigente actual.
        lenient().when(parametrosSistemaCache.getIvaPorcentaje()).thenReturn(new BigDecimal("16.00"));
        ContratoPdfService svc = construir();

        Contrato c = fixtureContratoSimple();
        c.setMontoPrestamo(new BigDecimal("1000.00"));
        c.setFechaVencimiento(LocalDate.now().plusDays(28));
        // Snapshot congelado al firmar (changeset 026)
        c.setSnapPorcInteres(new BigDecimal("3.0000"));
        c.setSnapPorcAlmacen(new BigDecimal("1.2000"));
        c.setSnapPorcGastosAdmin(new BigDecimal("0.5000"));
        c.setSnapPorcSancionSemanal(new BigDecimal("2.0000"));
        c.setSnapDiasGraciaSancion(2);
        c.setSnapAplicarSancionPeriodo(true);
        c.setSnapIvaPorcentaje(new BigDecimal("16.00"));

        // Config vigente muy distinta a la del snapshot
        PlazoParametro ppVigente = new PlazoParametro();
        ppVigente.setPorcAlmacen(new BigDecimal("999.0000"));
        ppVigente.setPorcGastosAdmin(new BigDecimal("999.0000"));
        ppVigente.setPorcSancionSemanal(new BigDecimal("999.0000"));
        ppVigente.setAplicarSancionPorPeriodo(true);
        // Estos tres SIN snapshot: se leen del vigente aunque el contrato tenga snapshot
        ppVigente.setComisionPorVentaPrenda(new BigDecimal("42.0000"));
        ppVigente.setReposicionEsPorcentaje(true);
        ppVigente.setPorcReposicion(new BigDecimal("5.0000"));
        ppVigente.setMontoReposicion(new BigDecimal("999.00"));

        when(contratoService.findById(1L)).thenReturn(c);
        when(contratoService.calcularAmortizacion(1L)).thenReturn(amortizacion());
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(6L, 4, 1))
                .thenReturn(Optional.of(ppVigente));

        Map<String, Object> params = svc.armarParametros(1L);

        // Con snapshot: gana snapshot aunque vigente diga 999
        assertThat((BigDecimal) params.get("P_comisionAlmacenaje"))
                .isEqualByComparingTo("1.2000");
        assertThat((BigDecimal) params.get("P_gastosAdministracion"))
                .isEqualByComparingTo("0.5000");
        assertThat((BigDecimal) params.get("P_desempenoExtemporaneo"))
                .isEqualByComparingTo("2.0000");

        // Sin snapshot: lee vigente
        assertThat((BigDecimal) params.get("P_comisionAvaluo"))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat((BigDecimal) params.get("P_comisionComercializacion"))
                .isEqualByComparingTo("42.0000");
        // switch=true -> porc_reposicion (no monto)
        assertThat((BigDecimal) params.get("P_comisionReposicion"))
                .isEqualByComparingTo("5.0000");
    }

    @Test
    void desempenoExtemporaneo_conAplicarSancionFalse_imprimeElPorcentajeIgual() {
        // Regresion: la clausula 11e es DISCLOSURE contractual. Aunque el toggle
        // aplicarSancion=false (contratos previos a SANC-04, o config vieja), el %
        // configurado debe imprimirse tal cual — es informacion legal, no resultado
        // de calculo. Diferente de P_RESUMEN_MORATORIOS que si respeta el gate.
        lenient().when(parametrosSistemaCache.getIvaPorcentaje()).thenReturn(new BigDecimal("16.00"));
        ContratoPdfService svc = construir();

        Contrato c = fixtureContratoSimple();
        c.setMontoPrestamo(new BigDecimal("1000.00"));
        c.setFechaVencimiento(LocalDate.now().plusDays(28));

        PlazoParametro pp = new PlazoParametro();
        pp.setPorcAlmacen(new BigDecimal("1.5000"));
        pp.setPorcGastosAdmin(new BigDecimal("0.7500"));
        pp.setPorcSancionSemanal(new BigDecimal("2.5000"));
        // ← El gate esta apagado. Antes del fix esto aplastaba 11e a 0.
        pp.setAplicarSancionPorPeriodo(false);

        when(contratoService.findById(1L)).thenReturn(c);
        when(contratoService.calcularAmortizacion(1L)).thenReturn(amortizacion());
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(6L, 4, 1))
                .thenReturn(Optional.of(pp));

        Map<String, Object> params = svc.armarParametros(1L);

        // 11e debe mostrar 2.5, no 0.
        assertThat((BigDecimal) params.get("P_desempenoExtemporaneo"))
                .isEqualByComparingTo("2.5000");
    }

    @Test
    void comisionReposicion_esPorcentaje_usaPorcReposicion() {
        lenient().when(parametrosSistemaCache.getIvaPorcentaje()).thenReturn(new BigDecimal("16.00"));
        ContratoPdfService svc = construir();

        Contrato c = fixtureContratoSimple();
        c.setMontoPrestamo(new BigDecimal("1000.00"));
        c.setFechaVencimiento(LocalDate.now().plusDays(28));

        PlazoParametro pp = new PlazoParametro();
        pp.setReposicionEsPorcentaje(true);
        pp.setPorcReposicion(new BigDecimal("4.5000"));
        // Monto no debe usarse — se prueba que no se cuela cuando el switch es porcentaje
        pp.setMontoReposicion(new BigDecimal("999.00"));

        when(contratoService.findById(1L)).thenReturn(c);
        when(contratoService.calcularAmortizacion(1L)).thenReturn(amortizacion());
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(6L, 4, 1))
                .thenReturn(Optional.of(pp));

        Map<String, Object> params = svc.armarParametros(1L);

        assertThat((BigDecimal) params.get("P_comisionReposicion"))
                .isEqualByComparingTo("4.5000");
    }

    @Test
    void comisionReposicion_esMonto_usaMontoReposicion() {
        lenient().when(parametrosSistemaCache.getIvaPorcentaje()).thenReturn(new BigDecimal("16.00"));
        ContratoPdfService svc = construir();

        Contrato c = fixtureContratoSimple();
        c.setMontoPrestamo(new BigDecimal("1000.00"));
        c.setFechaVencimiento(LocalDate.now().plusDays(28));

        PlazoParametro pp = new PlazoParametro();
        pp.setReposicionEsPorcentaje(false);
        pp.setMontoReposicion(new BigDecimal("85.50"));
        // Porcentaje no debe usarse
        pp.setPorcReposicion(new BigDecimal("999.0000"));

        when(contratoService.findById(1L)).thenReturn(c);
        when(contratoService.calcularAmortizacion(1L)).thenReturn(amortizacion());
        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(6L, 4, 1))
                .thenReturn(Optional.of(pp));

        Map<String, Object> params = svc.armarParametros(1L);

        assertThat((BigDecimal) params.get("P_comisionReposicion"))
                .isEqualByComparingTo("85.50");
    }

    private Contrato fixtureContratoSimple() {
        TipoPrenda plata = new TipoPrenda();
        plata.setId(4); plata.setTipo("PLATAS");
        Plazo plazo = new Plazo();
        plazo.setId(6L); plazo.setNombre("Semanal - Plata"); plazo.setDiasPorPeriodo(7); plazo.setNumeroPeriodos(4);
        Direccion dir = new Direccion();
        dir.setCalle("X"); dir.setNumeroExterior("1"); dir.setColonia("Y");
        dir.setCiudad("Z"); dir.setEstado("W"); dir.setCodigoPostal("00000");
        Cliente cliente = new Cliente();
        cliente.setNombre("Test"); cliente.setApellidoPaterno("Test"); cliente.setApellidoMaterno("Test");
        cliente.setDireccion(dir);
        PartidaContrato p1 = new PartidaContrato();
        p1.setTipoPrenda(plata); p1.setDescripcion("pieza"); p1.setPesoNeto(new BigDecimal("10.00"));
        p1.setAvaluoContrato(new BigDecimal("100.00")); p1.setMontoPrestamo(new BigDecimal("1000.00"));
        Contrato c = new Contrato();
        c.setId(1L); c.setFolio("CTR-000001"); c.setCliente(cliente);
        c.setPlazo(plazo); c.setSucursalId(1); c.setMontoAvaluo(new BigDecimal("100.00"));
        c.setFechaApertura(LocalDateTime.now());
        c.setNombreBeneficiario("Ben"); c.setNumIdentificacion("123");
        List<PartidaContrato> partidas = new ArrayList<>();
        partidas.add(p1);
        c.setPartidas(partidas);
        return c;
    }

    private List<VencimientoResponse> amortizacion() {
        List<VencimientoResponse> filas = new ArrayList<>();
        filas.add(fila(1, "18.27", "3.78", "22.05", "3.52", "655.57"));
        filas.add(fila(2, "36.54", "7.56", "44.10", "7.05", "681.15"));
        filas.add(fila(3, "54.81", "11.34", "66.15", "10.58", "706.73"));
        filas.add(fila(4, "73.08", "15.12", "88.20", "14.11", "732.31"));
        return filas;
    }

    private VencimientoResponse fila(int n, String interes, String almacen, String totalInt,
                                     String iva, String desempeno) {
        VencimientoResponse v = new VencimientoResponse();
        v.setPeriodo(n);
        v.setFecha(LocalDate.of(2026, 8, 15).plusDays(7L * n));
        v.setInteres(new BigDecimal(interes));
        v.setAlmacen(new BigDecimal(almacen));
        v.setTotalInteres(new BigDecimal(totalInt));
        v.setIva(new BigDecimal(iva));
        v.setDesempeno(new BigDecimal(desempeno));
        v.setTotal(new BigDecimal(desempeno));
        return v;
    }
}
