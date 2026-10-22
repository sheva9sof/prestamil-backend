package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.model.Cliente;
import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.Empresa;
import com.ignis.prestamil.model.PartidaContrato;
import com.ignis.prestamil.model.Sucursal;
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
 * Carta responsiva (D-12): datos dinámicos y texto realmente impreso por la plantilla.
 */
@ExtendWith(MockitoExtension.class)
class CartaResponsivaServiceTest {

    @Mock ContratoRepository contratoRepository;
    @Mock SucursalRepository sucursalRepository;

    CartaResponsivaService service;

    @BeforeEach
    void setUp() {
        service = new CartaResponsivaService(contratoRepository, sucursalRepository);

        Empresa empresa = new Empresa();
        empresa.setNombre("Prestamil");
        empresa.setRazonSocial("Activos y Comunicaciones Palmas S.A. de C.V.");
        Sucursal sucursal = new Sucursal();
        sucursal.setId(1);
        sucursal.setNombre("COSAMALOAPAN");
        sucursal.setEmpresa(empresa);
        lenient().when(sucursalRepository.findById(1)).thenReturn(Optional.of(sucursal));
    }

    /** El contrato 44008700 de la carta de ejemplo: una motocicleta. */
    private static Contrato contrato() {
        Cliente cliente = new Cliente();
        cliente.setNombre("Indra Isbel");
        cliente.setApellidoPaterno("Vergara");
        cliente.setApellidoMaterno("Bazan");

        Usuario valuador = new Usuario();
        valuador.setNombreUsuario("soporte");
        valuador.setNombre("Soporte Tecnico");
        valuador.setApellidos("Sistemas");

        PartidaContrato moto = new PartidaContrato();
        moto.setNumPartida(1);
        moto.setClavePrenda("MOTO");
        moto.setDescripcionGeneral("MOTOCICLETA DEPORTIVA");
        moto.setDescripcion("MOTOCICLETA TIPO CROSS AZUL CON NEGRO MODELO 2023 MARCA ITALIKA");
        moto.setCantidad(1);
        moto.setAvaluoContrato(new BigDecimal("18000.00"));
        moto.setMontoPrestamo(new BigDecimal("9000.00"));

        Contrato c = new Contrato();
        c.setId(1L);
        c.setFolio("44008700");
        c.setSucursalId(1);
        c.setCliente(cliente);
        c.setUsuario(valuador);
        c.setTipoIdentificacion("CREDENCIAL IFE");
        c.setNumIdentificacion("1133088559654");
        c.setFechaApertura(LocalDateTime.of(2026, 10, 9, 10, 30));
        List<PartidaContrato> partidas = new ArrayList<>(List.of(moto));
        partidas.forEach(p -> p.setContrato(c));
        c.setPartidas(partidas);
        return c;
    }

    @Test
    void armarParametros_llevaLosDatosDelContrato() {
        Map<String, Object> p = service.armarParametros(contrato());

        assertThat(p.get("P_CONTRATO")).isEqualTo("44008700");
        assertThat(p.get("P_CLIENTE")).isEqualTo("INDRA ISBEL VERGARA BAZAN");
        // La carta nombra a la casa de empeño por su razón social, no por el nombre comercial
        assertThat(p.get("P_EMPRESA")).isEqualTo("ACTIVOS Y COMUNICACIONES PALMAS S.A. DE C.V.");
        assertThat(p.get("P_IDENTIFICACION")).isEqualTo(
                "CREDENCIAL PARA VOTAR CON No. DE FOLIO: 1133088559654 EXPEDIDA POR EL INSTITUTO FEDERAL ELECTORAL");
        assertThat(p.get("P_DOCUMENTO_IDENTIFICACION")).isEqualTo("CREDENCIAL PARA VOTAR");
        assertThat(p.get("P_GENERO_IDENTIFICACION")).isEqualTo("A");
        assertThat(p.get("P_VALUADOR")).isEqualTo("SOPORTE TECNICO SISTEMAS");
    }

    @Test
    void armarParametros_pasaporte_concuerdaEnMasculino() {
        Contrato c = contrato();
        c.setTipoIdentificacion("PASAPORTE");
        c.setNumIdentificacion("G12345678");

        Map<String, Object> p = service.armarParametros(c);

        assertThat(p.get("P_IDENTIFICACION")).isEqualTo(
                "PASAPORTE CON No. DE FOLIO: G12345678 EXPEDIDO POR LA SECRETARÍA DE RELACIONES EXTERIORES");
        assertThat(p.get("P_GENERO_IDENTIFICACION")).isEqualTo("O");
    }

    @Test
    void armarParametros_sinIdentificacion_dejaEspacioParaLlenarAMano() {
        Contrato c = contrato();
        c.setTipoIdentificacion(null);
        c.setNumIdentificacion(null);

        Map<String, Object> p = service.armarParametros(c);

        assertThat(p.get("P_IDENTIFICACION")).isEqualTo("IDENTIFICACIÓN OFICIAL CON No. DE FOLIO: ________________");
        assertThat(p.get("P_DOCUMENTO_IDENTIFICACION")).isEqualTo("IDENTIFICACIÓN OFICIAL");
    }

    @Test
    void armarParametros_licencia_noInventaEmisor() {
        Contrato c = contrato();
        c.setTipoIdentificacion("LICENCIA DE MANEJO");

        assertThat(service.armarParametros(c).get("P_IDENTIFICACION"))
                .isEqualTo("LICENCIA DE MANEJO CON No. DE FOLIO: 1133088559654");
    }

    @Test
    void buildPartidas_llevaDescripcionAvaluoYPrestamo() {
        var filas = service.buildPartidas(contrato().getPartidas());

        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).getDescripcion()).isEqualTo(
                "MOTO - MOTOCICLETA DEPORTIVA\nMOTOCICLETA TIPO CROSS AZUL CON NEGRO MODELO 2023 MARCA ITALIKA");
        assertThat(filas.get(0).getAvaluo()).isEqualTo("$18,000.00");
        assertThat(filas.get(0).getPrestamo()).isEqualTo("$9,000.00");
    }

    /** El texto impreso trae los datos dinámicos dentro de los párrafos de la carta. */
    @Test
    void plantilla_imprimeLaCartaConLosDatosDelContrato() throws Exception {
        JasperPrint print = service.llenar(contrato());

        String texto = textoImpreso(print);
        assertThat(print.getPages()).hasSize(1);
        assertThat(texto)
                .contains("CARTA RESPONSIVA")
                .contains("YO INDRA ISBEL VERGARA BAZAN; CONSUMIDOR DE LA CASA DE EMPEÑO: "
                        + "\"ACTIVOS Y COMUNICACIONES PALMAS S.A. DE C.V.\"")
                .contains("IDENTIFICÁNDOME CON CREDENCIAL PARA VOTAR CON No. DE FOLIO: 1133088559654 EXPEDIDA POR "
                        + "EL INSTITUTO FEDERAL ELECTORAL, MISMA QUE EN ESTE CASO")
                .contains("SOLICITO ME SEA DEVUELTA POR SER DE UTILIDAD.")
                .contains("MOTOCICLETA TIPO CROSS AZUL CON NEGRO")
                .contains("$18,000.00").contains("$9,000.00")
                .contains("CLÁUSULA 13 DEL CONTRATO CON NÚMERO: 44008700 MISMO QUE")
                .contains("CONTRATO DE MUTUO No. 44008700 COMPROMETIÉNDOME")
                .contains("A LA EMPRESA ACTIVOS Y COMUNICACIONES PALMAS S.A. DE C.V. O PERSONA FÍSICA")
                .contains("COPIA DE MI CREDENCIAL PARA VOTAR ASÍ COMO COPIA DEL MISMO.")
                .contains("EL CONSUMIDOR")
                .contains("SOPORTE TECNICO SISTEMAS");
    }

    @Test
    void generarPdf_produceUnPdf() {
        when(contratoRepository.findById(1L)).thenReturn(Optional.of(contrato()));

        byte[] pdf = service.generarPdf(1L);

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
