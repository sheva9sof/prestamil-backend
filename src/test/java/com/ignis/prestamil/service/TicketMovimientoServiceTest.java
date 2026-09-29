package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.model.Banco;
import com.ignis.prestamil.model.Cliente;
import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.Direccion;
import com.ignis.prestamil.model.Empresa;
import com.ignis.prestamil.model.MovimientoContrato;
import com.ignis.prestamil.model.PartidaContrato;
import com.ignis.prestamil.model.Plazo;
import com.ignis.prestamil.model.PlazoParametro;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.model.TipoMovimiento;
import com.ignis.prestamil.model.TipoPrenda;
import com.ignis.prestamil.model.TipoTarjeta;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.MovimientoContratoRepository;
import com.ignis.prestamil.repository.PlazoParametroRepository;
import com.ignis.prestamil.repository.SucursalRepository;
import com.ignis.prestamil.service.calculo.CalculoContratoService;
import com.ignis.prestamil.service.calculo.ParametrosSistemaCache;
import com.ignis.prestamil.util.LineaTicketRow;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Ticket de movimiento (RN-25): contenido de la nota y generación real del PDF con la plantilla.
 */
@ExtendWith(MockitoExtension.class)
class TicketMovimientoServiceTest {

    @Mock MovimientoContratoRepository movimientoRepository;
    @Mock SucursalRepository sucursalRepository;
    @Mock PlazoParametroRepository plazoParametroRepository;
    @Mock ParametrosSistemaCache parametrosSistemaCache;

    TicketMovimientoService service;

    @BeforeEach
    void setUp() {
        service = new TicketMovimientoService(movimientoRepository, sucursalRepository, plazoParametroRepository,
                new CalculoContratoService(parametrosSistemaCache));
        lenient().when(parametrosSistemaCache.getIvaPorcentaje()).thenReturn(new BigDecimal("16.00"));

        Empresa empresa = new Empresa();
        empresa.setNombre("Prestamil");
        empresa.setRazonSocial("ACTIVOS Y COMUNICACIONES PALMAS S.A. DE C.V.");
        empresa.setCalle("Calle de prueba");
        empresa.setNoExterior("12");
        empresa.setColonia("Centro");
        empresa.setCp("03710");
        empresa.setEstado("CDMX");
        Sucursal sucursal = new Sucursal();
        sucursal.setId(1);
        sucursal.setNumeroSucursal(7);
        sucursal.setNombre("San Luis Acatlán");
        sucursal.setCalle("Morelos");
        sucursal.setColonia("Centro");
        sucursal.setMunicipio("San Luis Acatlán");
        sucursal.setEstado("Guerrero");
        sucursal.setCp("41600");
        sucursal.setTelefono("7411234567");
        sucursal.setEmpresa(empresa);
        lenient().when(sucursalRepository.findById(1)).thenReturn(Optional.of(sucursal));

        PlazoParametro pp = new PlazoParametro();
        pp.setPorcInteres(new BigDecimal("1.13"));
        pp.setPorcAlmacen(new BigDecimal("0.60"));
        pp.setPorcSancionSemanal(new BigDecimal("2.00"));
        pp.setAplicarSancionPorPeriodo(true);
        pp.setComisionPorVentaPrenda(new BigDecimal("18.00"));
        lenient().when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(1L, 1, 1))
                .thenReturn(Optional.of(pp));
    }

    // =========================================================================
    // Fixtures
    // =========================================================================

    private static Contrato contrato1493() {
        Direccion dir = new Direccion();
        dir.setCalle("Benito Juárez");
        dir.setNumeroExterior("201");
        dir.setColonia("Centro");
        dir.setCiudad("Pinotepa");
        dir.setEstado("Oaxaca");
        dir.setCodigoPostal("71600");
        Cliente cliente = new Cliente();
        cliente.setId(815);
        cliente.setNombre("María");
        cliente.setApellidoPaterno("López");
        cliente.setApellidoMaterno("Ruiz");
        cliente.setDireccion(dir);

        TipoPrenda alhaja = new TipoPrenda();
        alhaja.setId(1);
        alhaja.setTipo("ALHAJA");
        PartidaContrato partida = new PartidaContrato();
        partida.setNumPartida(1);
        partida.setTipoPrenda(alhaja);
        partida.setClavePrenda("AN14");
        partida.setDescripcion("ANILLO ORO 14K CON PIEDRA");
        partida.setKilataje(14);
        partida.setHechura("HE");
        partida.setPesoNeto(new BigDecimal("4.50"));

        Plazo plazo = new Plazo();
        plazo.setId(1L);
        plazo.setDiasPorPeriodo(7);
        plazo.setNumeroPeriodos(4);

        Contrato c = new Contrato();
        c.setId(42L);
        c.setFolio("1493");
        c.setCliente(cliente);
        c.setPlazo(plazo);
        c.setSucursalId(1);
        c.setMontoAvaluo(new BigDecimal("1500.00"));
        c.setMontoPrestamo(new BigDecimal("1195.00"));
        c.setSaldoCapital(new BigDecimal("1195.00"));
        c.setPartidas(List.of(partida));
        return c;
    }

    /** Refrendo C2 cobrado con pago mixto: $50 en efectivo y $45.92 con tarjeta de débito. */
    private static MovimientoContrato refrendoC2() {
        Usuario usuario = new Usuario();
        usuario.setNombreUsuario("cajero1");
        Banco bbva = new Banco();
        bbva.setNombre("BBVA");

        MovimientoContrato m = new MovimientoContrato();
        m.setId(7L);
        m.setContrato(contrato1493());
        m.setUsuario(usuario);
        m.setTipo(TipoMovimiento.RF);
        m.setFolioNota(27323);
        m.setFecha(LocalDateTime.of(2026, 8, 11, 12, 30));
        m.setMonto(new BigDecimal("95.92"));
        m.setInteres(new BigDecimal("82.69"));
        m.setSancion(new BigDecimal("0.00"));
        m.setIva(new BigDecimal("13.23"));
        m.setAbonoCapital(BigDecimal.ZERO);
        m.setPeriodosNormales(4);
        m.setSemanasVencidas(0);
        m.setSaldoNuevo(new BigDecimal("1195.00"));
        m.setFechaVencNueva(LocalDate.of(2026, 9, 10));
        m.setImporteEfectivo(new BigDecimal("50.00"));
        m.setImporteTarjeta(new BigDecimal("45.92"));
        m.setCambioEntregado(new BigDecimal("0.00"));
        m.setTipoTarjeta(TipoTarjeta.DEBITO);
        m.setTarjetaUltimos4("1234");
        m.setBancoEmisor(bbva);
        m.setAutorizacionBanco("A1B2C3");
        return m;
    }

    @SuppressWarnings("unchecked")
    private static List<LineaTicketRow> renglones(Map<String, Object> params, String parametro) {
        return List.copyOf((java.util.Collection<LineaTicketRow>) ((JRBeanCollectionDataSource) params.get(parametro)).getData());
    }

    // =========================================================================
    // Contenido
    // =========================================================================

    @Test
    void refrendo_imprimeFolioTotalesPagoRecibidoYNuevoVencimiento() {
        Map<String, Object> params = service.armarParametros(refrendoC2());

        assertThat(params.get("P_EMPRESA")).isEqualTo("PRESTAMIL");
        assertThat((String) params.get("P_EMPRESA_DATOS"))
                .contains("ACTIVOS Y COMUNICACIONES PALMAS")
                .contains("Calle de prueba 12, Centro, C.P. 03710, CDMX");
        assertThat((String) params.get("P_SUCURSAL")).contains("Expedido en la sucursal: 7 San Luis Acatlán")
                .contains("Tel. 7411234567");
        assertThat(params.get("P_FOLIO")).isEqualTo("Folio No: 27323");
        assertThat(params.get("P_FECHA")).isEqualTo("11/08/2026 12:30");
        assertThat(params.get("P_TIPO_MOVIMIENTO")).isEqualTo("REFRENDO (RF)");
        assertThat(params.get("P_CANCELADO")).isEqualTo(false);
        assertThat((String) params.get("P_CLIENTE")).startsWith("Cliente: 815 María López Ruiz")
                .contains("Benito Juárez 201");
        assertThat(params.get("P_TOTAL")).isEqualTo("$95.92");
        assertThat(params.get("P_TOTAL_LETRA")).isEqualTo("*** NOVENTA Y CINCO PESOS 92/100 M.N. ***");

        assertThat(renglones(params, "P_PARAMETROS"))
                .extracting(LineaTicketRow::getEtiqueta, LineaTicketRow::getValor)
                .contains(tuple("Préstamo original", "$1,195.00"), tuple("Nuevo préstamo", "$1,195.00"),
                        tuple("% Intereses", "1.13 %"), tuple("% Almacenaje", "0.60 %"),
                        tuple("G.Oper. x Vta.", "18.00 %"), tuple("Moratorios %", "2.00 %"), tuple("% IVA", "16.00 %"));
        assertThat(renglones(params, "P_MOVIMIENTO"))
                .extracting(LineaTicketRow::getEtiqueta, LineaTicketRow::getValor)
                .containsExactly(tuple("Periodos pagados", "4 normales / 0 extemp."),
                        tuple("Nuevo vencimiento", "10/09/2026"), tuple("Comercialización", "25/09/2026"));
        assertThat(renglones(params, "P_TOTALES"))
                .extracting(LineaTicketRow::getEtiqueta, LineaTicketRow::getValor)
                .containsExactly(tuple("Intereses", "$82.69"), tuple("Sanción", "$0.00"),
                        tuple("Subtotal", "$82.69"), tuple("IVA", "$13.23"));
        // "Pago" es lo recibido en la ventana de Cobro, no el total del contrato (defecto de COCAE)
        assertThat(renglones(params, "P_PAGO"))
                .extracting(LineaTicketRow::getEtiqueta, LineaTicketRow::getValor)
                .containsExactly(tuple("Efectivo", "$50.00"), tuple("Tarjeta de débito ****1234", "$45.92"),
                        tuple("Banco", "BBVA"), tuple("Autorización", "A1B2C3"),
                        tuple("Pago recibido", "$95.92"), tuple("Cambio", "$0.00"));
    }

    @Test
    void finiquito_imprimeElCapitalYNoUnNuevoVencimiento() {
        MovimientoContrato m = refrendoC2();
        m.setTipo(TipoMovimiento.FI);
        m.setMonto(new BigDecimal("1290.92"));
        m.setSaldoNuevo(BigDecimal.ZERO);

        Map<String, Object> params = service.armarParametros(m);

        assertThat(renglones(params, "P_TOTALES"))
                .extracting(LineaTicketRow::getEtiqueta, LineaTicketRow::getValor)
                .contains(tuple("Capital", "$1,195.00"));
        assertThat(renglones(params, "P_MOVIMIENTO")).extracting(LineaTicketRow::getEtiqueta)
                .doesNotContain("Nuevo vencimiento");
        assertThat(params.get("P_TOTAL_LETRA")).isEqualTo("*** MIL DOSCIENTOS NOVENTA PESOS 92/100 M.N. ***");
    }

    @Test
    void enGracia_imprimeLosDiasDeGraciaUsados() {
        MovimientoContrato m = refrendoC2();
        m.setTipo(TipoMovimiento.RPG);
        m.setDiasGraciaUsados(2);

        Map<String, Object> params = service.armarParametros(m);

        assertThat(params.get("P_TIPO_MOVIMIENTO")).isEqualTo("REFRENDO EN PERIODO DE GRACIA (RPG)");
        assertThat(renglones(params, "P_MOVIMIENTO"))
                .extracting(LineaTicketRow::getEtiqueta, LineaTicketRow::getValor)
                .contains(tuple("Días de gracia usados", "2"));
    }

    @Test
    void empeno_noGeneraNota() {
        MovimientoContrato m = refrendoC2();
        m.setTipo(TipoMovimiento.EMP);

        assertThatThrownBy(() -> service.armarParametros(m)).isInstanceOf(BadRequestException.class);
    }

    // =========================================================================
    // PDF real: la plantilla compila y se llena
    // =========================================================================

    @Test
    void generarPdf_produceUnPdfNoVacio() {
        when(movimientoRepository.findById(7L)).thenReturn(Optional.of(refrendoC2()));

        byte[] pdf = service.generarPdf(7L);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void generarPdf_movimientoCanceladoYSinFormaDePago_tambienSeImprime() {
        MovimientoContrato m = refrendoC2();
        m.setCancelado(true);
        m.setImporteEfectivo(null);
        m.setImporteTarjeta(null);
        when(movimientoRepository.findById(7L)).thenReturn(Optional.of(m));

        assertThat(service.generarPdf(7L)).isNotEmpty();
    }
}
