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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void generarPdf_contratoDePlata_produceUnPdfNoVacio() {
        ContratoPdfService pdfService =
                new ContratoPdfService(contratoService, plazoParametroRepository, sucursalRepository);

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
        p1.setHechura("F"); p1.setPesoGramos(new BigDecimal("20.00"));
        p1.setAvaluoContrato(new BigDecimal("162.50")); p1.setMontoPrestamo(new BigDecimal("130.00"));
        PartidaContrato p2 = new PartidaContrato();
        p2.setTipoPrenda(plata); p2.setDescripcion("cadena"); p2.setLey(new BigDecimal("720"));
        p2.setHechura("F"); p2.setPesoGramos(new BigDecimal("100.00"));
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
