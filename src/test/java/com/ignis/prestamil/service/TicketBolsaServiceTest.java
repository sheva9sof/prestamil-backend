package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.model.Cliente;
import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.Empresa;
import com.ignis.prestamil.model.PartidaContrato;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.model.TipoPrenda;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.ContratoRepository;
import com.ignis.prestamil.repository.SucursalRepository;
import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.JasperPrint;
import org.junit.jupiter.api.BeforeEach;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Ticket de bolsa (D-10): contenido de los parámetros y texto realmente impreso por la plantilla.
 */
@ExtendWith(MockitoExtension.class)
class TicketBolsaServiceTest {

    @Mock ContratoRepository contratoRepository;
    @Mock SucursalRepository sucursalRepository;

    TicketBolsaService service;

    @BeforeEach
    void setUp() {
        service = new TicketBolsaService(contratoRepository, sucursalRepository);

        Empresa empresa = new Empresa();
        empresa.setNombre("Prestamil");
        Sucursal sucursal = new Sucursal();
        sucursal.setId(1);
        sucursal.setNumeroSucursal(76);
        sucursal.setNombre("SAN LUIS ACATLAN");
        sucursal.setEmpresa(empresa);
        lenient().when(sucursalRepository.findById(1)).thenReturn(Optional.of(sucursal));
    }

    /** Contrato de dos partidas: una esclava (2 piezas, con peso total) y un anillo sin peso total. */
    private static Contrato contrato() {
        Cliente cliente = new Cliente();
        cliente.setId(815);
        cliente.setNombre("KARENT LETICIA");
        cliente.setApellidoPaterno("DE JESUS");
        cliente.setApellidoMaterno("NERI");

        Usuario valuador = new Usuario();
        valuador.setNombreUsuario("soporte");
        valuador.setNombre("Soporte Tecnico");
        valuador.setApellidos("Sistemas");

        TipoPrenda alhaja = new TipoPrenda();
        alhaja.setId(1);
        alhaja.setTipo("ALHAJA");

        PartidaContrato esclava = new PartidaContrato();
        esclava.setNumPartida(1);
        esclava.setCodigoBarras("0000000041");
        esclava.setTipoPrenda(alhaja);
        esclava.setClavePrenda("ESC");
        esclava.setDescripcionGeneral("ESCLAVA 14K");
        esclava.setDescripcion("ESCLAVA CON MARIPOSA, BROCHE ROTO");
        esclava.setCantidad(2);
        esclava.setKilataje(14);
        esclava.setHechura("N");
        esclava.setPesoNeto(new BigDecimal("6.0000"));
        esclava.setPesoTotal(new BigDecimal("7.5000"));

        PartidaContrato anillo = new PartidaContrato();
        anillo.setNumPartida(2);
        anillo.setCodigoBarras("0000000042");
        anillo.setTipoPrenda(alhaja);
        anillo.setClavePrenda("AN");
        anillo.setDescripcionGeneral("ANILLO 10K");
        anillo.setDescripcion("ANILLO LISO DE CABALLERO");
        anillo.setCantidad(1);
        anillo.setKilataje(10);
        anillo.setPesoNeto(new BigDecimal("2.5000"));

        Contrato c = new Contrato();
        c.setId(3967L);
        c.setFolio("CTR-003967");
        c.setNumBolsa(3969);
        c.setSucursalId(1);
        c.setCliente(cliente);
        c.setUsuario(valuador);
        c.setFechaApertura(LocalDateTime.of(2026, 8, 11, 10, 30));
        c.setFechaVencimiento(LocalDate.of(2026, 9, 8));
        c.setFechaComercializacion(LocalDate.of(2026, 9, 23));
        List<PartidaContrato> partidas = new ArrayList<>(List.of(esclava, anillo));
        partidas.forEach(p -> p.setContrato(c));
        c.setPartidas(partidas);
        return c;
    }

    @Test
    void armarParametros_incluyeTodosLosCamposDelTicket() {
        Map<String, Object> p = service.armarParametros(contrato());

        assertThat(p.get("P_CONTRATO")).isEqualTo("Contrato: CTR-003967");
        assertThat(p.get("P_BOLSA")).isEqualTo("3969");
        assertThat(p.get("P_CLIENTE")).isEqualTo("KARENT LETICIA DE JESUS NERI");
        assertThat(p.get("P_SUCURSAL")).isEqualTo("Sucursal: 76 SAN LUIS ACATLAN");
        // Piezas, no partidas: la esclava agrupa 2 piezas
        assertThat(p.get("P_PIEZAS")).isEqualTo("3");
        // Peso físico: 7.50 (total de la esclava) + 2.50 (neto del anillo, que no tiene total)
        assertThat(p.get("P_PESO_TOTAL")).isEqualTo("10.00 g");
        assertThat(p.get("P_VALUADOR")).isEqualTo("SOPORTE TECNICO SISTEMAS");
        assertThat(p.get("P_FECHA_EMPENO")).isEqualTo("11/08/2026");
        assertThat(p.get("P_FECHA_COMERCIALIZACION")).isEqualTo("23/09/2026");
    }

    @Test
    void buildPartidas_llevaClaveDescripcionGeneralYEspecifica_yCodigoDeBarras() {
        var filas = service.buildPartidas(contrato().getPartidas());

        assertThat(filas).hasSize(2);
        assertThat(filas.get(0).getPartida()).isEqualTo("1");
        assertThat(filas.get(0).getDescripcion()).isEqualTo("ESC - ESCLAVA 14K\nESCLAVA CON MARIPOSA, BROCHE ROTO");
        assertThat(filas.get(0).getDetalle()).isEqualTo("2 pzas · 14K / N · 7.50 g · Cód. 0000000041");
        assertThat(filas.get(1).getDescripcion()).isEqualTo("AN - ANILLO 10K\nANILLO LISO DE CABALLERO");
        assertThat(filas.get(1).getDetalle()).isEqualTo("1 pza · 10K · 2.50 g · Cód. 0000000042");
    }

    @Test
    void armarParametros_sinPesos_noInventaPeso_ySinFechaComercializacionLaDerivaDelVencimiento() {
        Contrato c = contrato();
        c.getPartidas().forEach(p -> {
            p.setPesoNeto(null);
            p.setPesoTotal(null);
        });
        c.setFechaComercializacion(null);

        Map<String, Object> p = service.armarParametros(c);

        assertThat(p.get("P_PESO_TOTAL")).isEqualTo("—");
        // Vencimiento 08/09/2026 + 15 días
        assertThat(p.get("P_FECHA_COMERCIALIZACION")).isEqualTo("23/09/2026");
    }

    /** El texto impreso por la plantilla trae todos los campos del ticket, no solo los parámetros. */
    @Test
    void plantilla_imprimeTodosLosCampos() throws Exception {
        JasperPrint print = service.llenar(contrato());

        String texto = textoImpreso(print);
        assertThat(print.getPageWidth()).isEqualTo(227); // mismo ancho que la nota de movimiento
        assertThat(texto)
                .contains("TICKET DE BOLSA")
                .contains("Contrato: CTR-003967")
                .contains("BOLSA")
                .contains("3969")
                .contains("ESC - ESCLAVA 14K")
                .contains("ESCLAVA CON MARIPOSA, BROCHE ROTO")
                .contains("AN - ANILLO 10K")
                .contains("ANILLO LISO DE CABALLERO")
                .contains("Piezas").contains("3")
                .contains("Peso total").contains("10.00 g")
                .contains("Valuador:").contains("SOPORTE TECNICO SISTEMAS")
                .contains("Fecha de empeño:").contains("11/08/2026")
                .contains("Venta al público:").contains("23/09/2026");
    }

    @Test
    void generarPdf_produceUnPdf() {
        when(contratoRepository.findById(3967L)).thenReturn(Optional.of(contrato()));

        byte[] pdf = service.generarPdf(3967L);

        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void generarPdf_contratoInexistente_404() {
        when(contratoRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generarPdf(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    private static String textoImpreso(JasperPrint print) {
        StringBuilder sb = new StringBuilder();
        for (JRPrintPage page : print.getPages()) {
            for (JRPrintElement e : page.getElements()) {
                if (e instanceof JRPrintText t) {
                    sb.append(t.getFullText()).append('\n');
                }
            }
        }
        return sb.toString();
    }
}
