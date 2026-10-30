package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.model.Caja;
import com.ignis.prestamil.model.Denominacion;
import com.ignis.prestamil.model.Empresa;
import com.ignis.prestamil.model.EstatusTraspasoCaja;
import com.ignis.prestamil.model.Rol;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.model.TipoCaja;
import com.ignis.prestamil.model.TipoDenominacion;
import com.ignis.prestamil.model.TipoTraspasoCaja;
import com.ignis.prestamil.model.TraspasoCaja;
import com.ignis.prestamil.model.TraspasoCajaDenominacion;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.DenominacionRepository;
import com.ignis.prestamil.repository.SucursalRepository;
import com.ignis.prestamil.repository.TraspasoCajaRepository;
import com.ignis.prestamil.util.DenominacionTicketRow;
import com.ignis.prestamil.util.LineaTicketRow;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Ticket de traspaso entre cajas (CAJ-03, RC-04): contenido y generación real del PDF con la plantilla.
 */
@ExtendWith(MockitoExtension.class)
class TicketTraspasoCajaServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");
    private static final String[] VALORES =
            {"1000", "500", "200", "100", "50", "20", "10", "5", "2", "1", "0.50", "0.20", "0.10", "0.01"};

    @Mock TraspasoCajaRepository traspasoCajaRepository;
    @Mock DenominacionRepository denominacionRepository;
    @Mock SucursalRepository sucursalRepository;

    TicketTraspasoCajaService service;

    /** Las 14 de COCAE (074), en orden; id = orden. */
    private final List<Denominacion> catalogo = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Clock reloj = Clock.fixed(LocalDate.of(2026, 10, 30).atTime(18, 5).atZone(ZONA).toInstant(), ZONA);
        service = new TicketTraspasoCajaService(traspasoCajaRepository, denominacionRepository,
                sucursalRepository, reloj);

        Empresa empresa = new Empresa();
        empresa.setNombre("Prestamil");
        empresa.setRazonSocial("ACTIVOS Y COMUNICACIONES PALMAS S.A. DE C.V.");
        Sucursal sucursal = new Sucursal();
        sucursal.setId(1);
        sucursal.setNumeroSucursal(2);
        sucursal.setNombre("Pinotepa 2");
        sucursal.setEmpresa(empresa);
        lenient().when(sucursalRepository.findById(1)).thenReturn(Optional.of(sucursal));

        for (int i = 0; i < VALORES.length; i++) {
            Denominacion d = new Denominacion();
            d.setId(i + 1);
            d.setValor(new BigDecimal(VALORES[i]).setScale(2));
            d.setTipo(i < 6 ? TipoDenominacion.BILLETE : TipoDenominacion.MONEDA);
            d.setOrden(i + 1);
            catalogo.add(d);
        }
        lenient().when(denominacionRepository.findByActivaTrueOrderByOrdenAsc()).thenReturn(catalogo);
    }

    // =========================================================================
    // Fixtures
    // =========================================================================

    private static Caja caja(TipoCaja tipo, String clave, String nombre) {
        Caja c = new Caja();
        c.setSucursalId(1);
        c.setTipo(tipo);
        c.setClave(clave);
        c.setNombre(nombre);
        return c;
    }

    private static Usuario usuario(String nombre, String apellidos, String rol) {
        Rol r = new Rol();
        r.setRol(rol);
        Usuario u = new Usuario();
        u.setNombreUsuario(nombre.toLowerCase());
        u.setNombre(nombre);
        u.setApellidos(apellidos);
        u.setRol(r);
        return u;
    }

    private Denominacion denominacion(String valor) {
        return catalogo.stream().filter(d -> d.getValor().compareTo(new BigDecimal(valor)) == 0)
                .findFirst().orElseThrow();
    }

    private static void renglon(TraspasoCaja t, Denominacion d, int cantidad) {
        TraspasoCajaDenominacion r = new TraspasoCajaDenominacion();
        r.setTraspaso(t);
        r.setDenominacion(d);
        r.setCantidad(cantidad);
        r.setImporte(d.getValor().multiply(BigDecimal.valueOf(cantidad)).setScale(2));
        t.getDesglose().add(r);
    }

    /** Ticket 4355 de COCAE: dotación de 1,000 de la bóveda a la ventanilla. */
    private TraspasoCaja dotacion4355() {
        TraspasoCaja t = new TraspasoCaja();
        t.setId(3L);
        t.setSucursalId(1);
        t.setFolio(4355L);
        t.setTipo(TipoTraspasoCaja.DOTACION);
        t.setCajaOrigen(caja(TipoCaja.BOVEDA, "002", "BÓVEDA"));
        t.setCajaDestino(caja(TipoCaja.VENTANILLA, "003", "VENTANILLA"));
        t.setImporte(new BigDecimal("1000.00"));
        t.setFechaOperacion(LocalDate.of(2026, 10, 30));
        t.setFechaRegistro(LocalDateTime.of(2026, 10, 30, 9, 15));
        t.setUsuario(usuario("Ana", "Pérez", "Cajero"));
        renglon(t, denominacion("500"), 1);
        renglon(t, denominacion("200"), 2);
        renglon(t, denominacion("50"), 2);
        return t;
    }

    /** Concentración de 2,000 al banco con su ficha. */
    private TraspasoCaja concentracionConFicha() {
        TraspasoCaja t = new TraspasoCaja();
        t.setId(4L);
        t.setSucursalId(1);
        t.setFolio(4354L);
        t.setTipo(TipoTraspasoCaja.CONCENTRACION);
        t.setCajaOrigen(caja(TipoCaja.BOVEDA, "002", "BÓVEDA"));
        t.setCajaDestino(caja(TipoCaja.CONCENTRADORA, "001", "CUENTA CONCENTRADORA"));
        t.setImporte(new BigDecimal("2000.00"));
        t.setFechaOperacion(LocalDate.of(2026, 10, 30));
        t.setFechaRegistro(LocalDateTime.of(2026, 10, 30, 9, 0));
        t.setUsuario(usuario("Luis", "Gómez", "Gerente"));
        t.setReferenciaBancaria("FICHA 778812");
        renglon(t, denominacion("1000"), 2);
        return t;
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> filas(Object dataSource) {
        return new ArrayList<>((java.util.Collection<T>) ((JRBeanCollectionDataSource) dataSource).getData());
    }

    // =========================================================================
    // Contenido
    // =========================================================================

    @Test
    void dotacion_imprimeEncabezadoTituloCajasYPuesto() {
        Map<String, Object> params = service.armarParametros(dotacion4355());

        assertThat(params.get("P_FECHA_IMPRESION")).isEqualTo("Impreso: 30/10/2026 18:05");
        assertThat(params.get("P_EMPRESA")).isEqualTo("PRESTAMIL");
        assertThat(params.get("P_EMPRESA_DATOS")).isEqualTo("ACTIVOS Y COMUNICACIONES PALMAS S.A. DE C.V.");
        assertThat(params.get("P_SUCURSAL")).isEqualTo("Sucursal: 2 Pinotepa 2");
        assertThat(params.get("P_TITULO")).isEqualTo("DOTACIÓN: 4355");
        assertThat(params.get("P_CANCELADO")).isEqualTo(false);
        assertThat(params.get("P_CANCELACION")).isNull();
        assertThat(params.get("P_TOTAL")).isEqualTo("$1,000.00");
        assertThat(params.get("P_CAJA_ORIGEN")).isEqualTo("002 BÓVEDA");
        assertThat(params.get("P_CAJA_DESTINO")).isEqualTo("003 VENTANILLA");

        List<LineaTicketRow> datos = filas(params.get("P_DATOS"));
        assertThat(datos).extracting(LineaTicketRow::getEtiqueta, LineaTicketRow::getValor).containsExactly(
                tuple("Fecha de operación", "30/10/2026"),
                tuple("Caja origen", "002 BÓVEDA"),
                tuple("Caja destino", "003 VENTANILLA"),
                tuple("Usuario", "ANA PÉREZ"),
                tuple("Puesto", "CAJERO"));
    }

    @Test
    void titulo_llevaElNombreDeCadaTipoConAcento() {
        TraspasoCaja t = dotacion4355();
        for (Object[] caso : new Object[][]{
                {TipoTraspasoCaja.MINISTRACION, "MINISTRACIÓN: 4355"},
                {TipoTraspasoCaja.CONCENTRACION, "CONCENTRACIÓN: 4355"},
                {TipoTraspasoCaja.RECOLECCION, "RECOLECCIÓN: 4355"}}) {
            t.setTipo((TipoTraspasoCaja) caso[0]);
            assertThat(service.armarParametros(t).get("P_TITULO")).isEqualTo(caso[1]);
        }
    }

    @Test
    void concentracion_imprimeLaReferenciaBancaria() {
        Map<String, Object> params = service.armarParametros(concentracionConFicha());

        assertThat(params.get("P_TITULO")).isEqualTo("CONCENTRACIÓN: 4354");
        List<LineaTicketRow> datos = filas(params.get("P_DATOS"));
        assertThat(datos).extracting(LineaTicketRow::getEtiqueta, LineaTicketRow::getValor).contains(
                tuple("Caja destino", "001 CUENTA CONCENTRADORA"),
                tuple("Puesto", "GERENTE"),
                tuple("Referencia bancaria", "FICHA 778812"));
    }

    @Test
    void renglones_traeLas14DenominacionesAunqueEstenEnCero() {
        List<DenominacionTicketRow> renglones = service.renglones(dotacion4355());

        assertThat(renglones).hasSize(14);
        assertThat(renglones).extracting(DenominacionTicketRow::getCantidad, DenominacionTicketRow::getDenominacion,
                        DenominacionTicketRow::getImporte)
                .startsWith(
                        tuple("0", "$1,000.00", "$0.00"),
                        tuple("1", "$500.00", "$500.00"),
                        tuple("2", "$200.00", "$400.00"),
                        tuple("0", "$100.00", "$0.00"),
                        tuple("2", "$50.00", "$100.00"))
                .endsWith(tuple("0", "$0.01", "$0.00"));
    }

    @Test
    void renglones_denominacionDesactivadaDespuesDelTraspaso_sigueApareciendoEnSuLugar() {
        TraspasoCaja t = dotacion4355();
        Denominacion veinteCentavos = denominacion("0.20");
        renglon(t, veinteCentavos, 5);
        // Se desactivó después del traspaso: ya no viene en el catálogo activo
        when(denominacionRepository.findByActivaTrueOrderByOrdenAsc())
                .thenReturn(catalogo.stream().filter(d -> d != veinteCentavos).toList());

        List<DenominacionTicketRow> renglones = service.renglones(t);

        assertThat(renglones).hasSize(14);
        assertThat(renglones.get(11)).extracting(DenominacionTicketRow::getCantidad,
                DenominacionTicketRow::getDenominacion, DenominacionTicketRow::getImporte)
                .containsExactly("5", "$0.20", "$1.00");
    }

    @Test
    void cancelado_marcaCanceladoConFechaUsuarioYMotivo() {
        TraspasoCaja t = dotacion4355();
        t.setEstatus(EstatusTraspasoCaja.CANCELADO);
        t.setFechaCancelacion(LocalDateTime.of(2026, 10, 30, 11, 40));
        t.setUsuarioCancela(usuario("Soporte", "Sistemas", "Sistemas"));
        t.setMotivoCancelacion("Se capturó el importe equivocado");

        Map<String, Object> params = service.armarParametros(t);

        assertThat(params.get("P_CANCELADO")).isEqualTo(true);
        assertThat(params.get("P_CANCELACION")).isEqualTo(
                "Cancelado el 30/10/2026 11:40 por SOPORTE SISTEMAS\nMotivo: Se capturó el importe equivocado");
    }

    // =========================================================================
    // PDF
    // =========================================================================

    @Test
    void generarPdf_activoYCancelado_producenPdf() throws Exception {
        TraspasoCaja activo = concentracionConFicha();
        TraspasoCaja cancelado = dotacion4355();
        cancelado.setEstatus(EstatusTraspasoCaja.CANCELADO);
        cancelado.setFechaCancelacion(LocalDateTime.of(2026, 10, 30, 11, 40));
        cancelado.setUsuarioCancela(usuario("Soporte", "Sistemas", "Sistemas"));
        cancelado.setMotivoCancelacion("Se capturó el importe equivocado");
        when(traspasoCajaRepository.findById(4L)).thenReturn(Optional.of(activo));
        when(traspasoCajaRepository.findById(3L)).thenReturn(Optional.of(cancelado));

        byte[] pdfActivo = service.generarPdf(4L);
        byte[] pdfCancelado = service.generarPdf(3L);

        assertThat(new String(pdfActivo, 0, 4)).isEqualTo("%PDF");
        assertThat(new String(pdfCancelado, 0, 4)).isEqualTo("%PDF");

        // Para inspección visual en el ancho de la impresora térmica
        Path dir = Path.of("target");
        Files.createDirectories(dir);
        Files.write(dir.resolve("ticket-traspaso-caja.pdf"), pdfActivo);
        Files.write(dir.resolve("ticket-traspaso-caja-cancelado.pdf"), pdfCancelado);
    }

    @Test
    void generarPdf_traspasoInexistente_404() {
        when(traspasoCajaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generarPdf(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }
}
