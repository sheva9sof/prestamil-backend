package com.ignis.prestamil.service;

import com.ignis.prestamil.model.Cliente;
import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.Direccion;
import com.ignis.prestamil.model.PartidaContrato;
import com.ignis.prestamil.model.PaseAlmoneda;
import com.ignis.prestamil.model.PaseAlmonedaDetalle;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.model.TipoCambioPase;
import com.ignis.prestamil.repository.PaseAlmonedaDetalleRepository;
import com.ignis.prestamil.repository.SucursalRepository;
import com.ignis.prestamil.response.CarteraVencidaRow;
import com.ignis.prestamil.response.PaseAVentaRow;
import com.ignis.prestamil.response.ResultadosPaseAlmonedaResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Reporte "Resultados del pase de almoneda" (C-11). Verifica el mapeo a DTO, el fallback de fecha
 * sin pase, la serializacion CSV y los reportes PDF.
 */
@ExtendWith(MockitoExtension.class)
class PaseAlmonedaReporteServiceTest {

    private static final int SUCURSAL = 1;
    private static final LocalDate FECHA = LocalDate.of(2026, 10, 6);

    @Mock
    PaseAlmonedaDetalleRepository detalleRepository;

    @Mock
    SucursalRepository sucursalRepository;

    @InjectMocks
    PaseAlmonedaReporteService service;

    @Test
    void obtener_fechaSinPase_devuelveListasVacias() {
        when(detalleRepository.findByPaseSucursalIdAndPaseFechaAndTipoCambio(
                eq(SUCURSAL), eq(FECHA), any())).thenReturn(List.of());

        ResultadosPaseAlmonedaResponse res = service.obtener(SUCURSAL, FECHA);

        assertThat(res.fecha()).isEqualTo(FECHA);
        assertThat(res.carteraVencida()).isEmpty();
        assertThat(res.paseAVenta()).isEmpty();
    }

    @Test
    void obtener_mapeaCarteraVencidaYPaseAVenta() {
        Contrato c1 = contrato(1L, "C-001", "1050.00");
        c1.setFechaVencimiento(LocalDate.of(2026, 9, 15));
        PaseAlmonedaDetalle vencido = detalle(c1, null, TipoCambioPase.VENCIDO);

        Contrato c2 = contrato(2L, "C-002", "0");
        PartidaContrato partida = new PartidaContrato();
        partida.setId(900L);
        partida.setNumPartida(3);
        partida.setDescripcion("Collar oro 18k");
        PaseAlmonedaDetalle enVenta = detalle(c2, partida, TipoCambioPase.EN_VENTA);

        when(detalleRepository.findByPaseSucursalIdAndPaseFechaAndTipoCambio(
                SUCURSAL, FECHA, TipoCambioPase.VENCIDO)).thenReturn(List.of(vencido));
        when(detalleRepository.findByPaseSucursalIdAndPaseFechaAndTipoCambio(
                SUCURSAL, FECHA, TipoCambioPase.EN_VENTA)).thenReturn(List.of(enVenta));

        ResultadosPaseAlmonedaResponse res = service.obtener(SUCURSAL, FECHA);

        assertThat(res.carteraVencida()).singleElement().satisfies(fila -> {
            assertThat(fila.folioContrato()).isEqualTo("C-001");
            assertThat(fila.cliente()).isEqualTo("Juan Perez Lopez");
            assertThat(fila.telefono()).isEqualTo("555-1234");
            assertThat(fila.direccion()).contains("Reforma").contains("Centro").contains("06000");
            assertThat(fila.fechaVencimiento()).isEqualTo(LocalDate.of(2026, 9, 15));
            assertThat(fila.saldoCapital()).isEqualByComparingTo("1050.00");
        });
        assertThat(res.paseAVenta()).singleElement().satisfies(fila -> {
            assertThat(fila.folioContrato()).isEqualTo("C-002");
            assertThat(fila.numPartida()).isEqualTo(3);
            assertThat(fila.descripcion()).isEqualTo("Collar oro 18k");
            assertThat(fila.fechaPase()).isEqualTo(FECHA);
        });
    }

    @Test
    void exportarCarteraVencidaCsv_incluyeBomUtf8YCabecera() {
        when(detalleRepository.findByPaseSucursalIdAndPaseFechaAndTipoCambio(
                eq(SUCURSAL), eq(FECHA), any())).thenReturn(List.of());

        byte[] bytes = service.exportarCarteraVencidaCsv(SUCURSAL, FECHA);

        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
        String cuerpo = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        assertThat(cuerpo).startsWith("Contrato,Cliente,Direccion,Telefono,Vencimiento,Saldo\n");
    }

    @Test
    void exportarCarteraVencidaPdf_conFilas_generaPdf() {
        Contrato c1 = contrato(1L, "C-001", "1050.00");
        when(detalleRepository.findByPaseSucursalIdAndPaseFechaAndTipoCambio(
                SUCURSAL, FECHA, TipoCambioPase.VENCIDO)).thenReturn(List.of(detalle(c1, null, TipoCambioPase.VENCIDO)));
        when(detalleRepository.findByPaseSucursalIdAndPaseFechaAndTipoCambio(
                SUCURSAL, FECHA, TipoCambioPase.EN_VENTA)).thenReturn(List.of());
        when(sucursalRepository.findById(SUCURSAL)).thenReturn(Optional.of(sucursal()));

        byte[] pdf = service.exportarCarteraVencidaPdf(SUCURSAL, FECHA);

        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void exportarPaseAVentaPdf_conFilas_generaPdf() {
        PartidaContrato partida = new PartidaContrato();
        partida.setNumPartida(1);
        partida.setDescripcion("Anillo oro 14k con piedra");
        when(detalleRepository.findByPaseSucursalIdAndPaseFechaAndTipoCambio(
                SUCURSAL, FECHA, TipoCambioPase.VENCIDO)).thenReturn(List.of());
        when(detalleRepository.findByPaseSucursalIdAndPaseFechaAndTipoCambio(
                SUCURSAL, FECHA, TipoCambioPase.EN_VENTA))
                .thenReturn(List.of(detalle(contrato(2L, "C-002", "0"), partida, TipoCambioPase.EN_VENTA)));
        when(sucursalRepository.findById(SUCURSAL)).thenReturn(Optional.of(sucursal()));

        byte[] pdf = service.exportarPaseAVentaPdf(SUCURSAL, FECHA);

        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void exportarPdf_fechaSinPaseYSinSucursal_generaPdfVacio() {
        when(detalleRepository.findByPaseSucursalIdAndPaseFechaAndTipoCambio(
                eq(SUCURSAL), eq(FECHA), any())).thenReturn(List.of());
        when(sucursalRepository.findById(SUCURSAL)).thenReturn(Optional.empty());

        assertThat(new String(service.exportarCarteraVencidaPdf(SUCURSAL, FECHA), 0, 4)).isEqualTo("%PDF");
        assertThat(new String(service.exportarPaseAVentaPdf(SUCURSAL, FECHA), 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void parametrosCarteraVencida_sumaSaldosYArmaEncabezado() {
        when(sucursalRepository.findById(SUCURSAL)).thenReturn(Optional.of(sucursal()));
        List<CarteraVencidaRow> filas = List.of(
                CarteraVencidaRow.builder().folioContrato("C-001").saldoCapital(new BigDecimal("1050.00")).build(),
                CarteraVencidaRow.builder().folioContrato("C-002").saldoCapital(new BigDecimal("200.50")).build(),
                CarteraVencidaRow.builder().folioContrato("C-003").build());

        Map<String, Object> params = service.parametrosCarteraVencida(SUCURSAL, FECHA, filas);

        assertThat(params.get("P_TOTALES")).isEqualTo("Contratos: 3     Saldo total: $1,250.50");
        assertThat(params.get("P_SIN_DATOS")).isEqualTo(false);
        assertThat(params.get("P_FECHA_PASE")).isEqualTo("Fecha del pase: 06/10/2026");
        assertThat((String) params.get("P_SUCURSAL")).startsWith("Sucursal 7 Centro");
    }

    @Test
    void parametrosPaseAVenta_sinFilas_marcaSinDatos() {
        when(sucursalRepository.findById(SUCURSAL)).thenReturn(Optional.empty());

        Map<String, Object> params = service.parametrosPaseAVenta(SUCURSAL, FECHA, List.of());

        assertThat(params.get("P_TOTALES")).isEqualTo("Prendas: 0");
        assertThat(params.get("P_SIN_DATOS")).isEqualTo(true);
        assertThat(params.get("P_EMPRESA")).isEqualTo("PRESTAMIL");
        assertThat(params.get("P_SUCURSAL")).isEqualTo("");
    }

    private Sucursal sucursal() {
        Sucursal s = new Sucursal();
        s.setId(SUCURSAL);
        s.setNumeroSucursal(7);
        s.setNombre("Centro");
        s.setCalle("Juarez");
        s.setNoExterior("12");
        return s;
    }

    private PaseAlmonedaDetalle detalle(Contrato c, PartidaContrato p, TipoCambioPase tipo) {
        PaseAlmoneda pase = new PaseAlmoneda();
        pase.setSucursalId(SUCURSAL);
        pase.setFecha(FECHA);

        PaseAlmonedaDetalle d = new PaseAlmonedaDetalle();
        d.setPase(pase);
        d.setContrato(c);
        d.setPartida(p);
        d.setTipoCambio(tipo);
        return d;
    }

    private Contrato contrato(long id, String folio, String saldo) {
        Direccion d = new Direccion();
        d.setCalle("Av. Reforma");
        d.setNumeroExterior("100");
        d.setColonia("Centro");
        d.setCiudad("CDMX");
        d.setEstado("CDMX");
        d.setCodigoPostal("06000");

        Cliente cliente = new Cliente();
        cliente.setNombre("Juan");
        cliente.setApellidoPaterno("Perez");
        cliente.setApellidoMaterno("Lopez");
        cliente.setTelefono("555-1234");
        cliente.setDireccion(d);

        Contrato c = new Contrato();
        c.setId(id);
        c.setFolio(folio);
        c.setCliente(cliente);
        c.setSaldoCapital(new BigDecimal(saldo));
        c.setFechaVencimiento(FECHA.minusDays(5));
        return c;
    }
}
