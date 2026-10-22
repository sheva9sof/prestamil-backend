package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.Empresa;
import com.ignis.prestamil.model.PartidaContrato;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.repository.ContratoRepository;
import com.ignis.prestamil.repository.SucursalRepository;
import com.ignis.prestamil.util.FormatoDocumento;
import com.ignis.prestamil.util.PartidaCartaRow;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Carta responsiva del contrato (D-12), impresa entre el contrato y el ticket de bolsa (D-10). El texto
 * legal vive en la plantilla; aquí se arman los datos que cambian por contrato: número de contrato,
 * cliente, razón social de la casa de empeño, identificación presentada, valuador y las prendas con su
 * avalúo y préstamo.
 */
@Service
public class CartaResponsivaService {

    static final String PLANTILLA = "jasper/carta-responsiva.jrxml";
    private static final String SIN_DATO = "________________";

    private final ContratoRepository contratoRepository;
    private final SucursalRepository sucursalRepository;

    private JasperReport reporte; // cacheado (la plantilla no cambia en runtime)

    public CartaResponsivaService(ContratoRepository contratoRepository, SucursalRepository sucursalRepository) {
        this.contratoRepository = contratoRepository;
        this.sucursalRepository = sucursalRepository;
    }

    /**
     * Genera la carta responsiva de un contrato.
     *
     * @param contratoId identificador del contrato
     * @return bytes del PDF
     * @throws ResourceNotFoundException si el contrato no existe
     */
    @Transactional(readOnly = true)
    public byte[] generarPdf(Long contratoId) {
        Contrato contrato = contratoRepository.findById(contratoId)
                .orElseThrow(() -> new ResourceNotFoundException("Contrato no encontrado: " + contratoId));
        try {
            return JasperExportManager.exportReportToPdf(llenar(contrato));
        } catch (JRException e) {
            throw new BadRequestException("No se pudo generar la carta responsiva: " + e.getMessage());
        }
    }

    /** Llena la plantilla. Package-private para leer en tests el texto que realmente se imprime. */
    JasperPrint llenar(Contrato contrato) throws JRException {
        List<PartidaContrato> partidas = contrato.getPartidas() != null ? contrato.getPartidas() : List.of();
        return JasperFillManager.fillReport(getReporte(), armarParametros(contrato),
                new JRBeanCollectionDataSource(buildPartidas(partidas)));
    }

    /**
     * Parámetros de la plantilla. Package-private para verificar el contenido en tests sin leer el PDF.
     */
    Map<String, Object> armarParametros(Contrato contrato) {
        Sucursal sucursal = sucursalRepository.findById(contrato.getSucursalId()).orElse(null);
        Empresa empresa = sucursal != null ? sucursal.getEmpresa() : null;
        Identificacion id = Identificacion.de(contrato.getTipoIdentificacion());

        Map<String, Object> params = new HashMap<>();
        params.put("P_CONTRATO", nz(contrato.getFolio()));
        params.put("P_CLIENTE", contrato.getCliente() != null
                ? FormatoDocumento.nombreCompleto(contrato.getCliente()).toUpperCase(Locale.ROOT) : SIN_DATO);
        params.put("P_EMPRESA", razonSocial(empresa));

        // "CREDENCIAL PARA VOTAR CON No. DE FOLIO: 1133088559654 EXPEDIDA POR EL INSTITUTO FEDERAL ELECTORAL"
        String folio = contrato.getNumIdentificacion() != null && !contrato.getNumIdentificacion().isBlank()
                ? contrato.getNumIdentificacion().trim() : SIN_DATO;
        params.put("P_IDENTIFICACION", id.nombre() + " CON No. DE FOLIO: " + folio
                + (id.emisor() != null ? " EXPEDID" + id.genero() + " POR " + id.emisor() : ""));
        params.put("P_DOCUMENTO_IDENTIFICACION", id.nombre());
        // Concordancia del texto fijo: "MISMA QUE ... ME SEA DEVUELTA" / "MISMO QUE ... DEVUELTO"
        params.put("P_GENERO_IDENTIFICACION", id.genero());
        params.put("P_VALUADOR", FormatoDocumento.nombreUsuario(contrato.getUsuario()));
        return params;
    }

    /** Filas de prendas: descripción (clave, general y específica), avalúo del contrato y préstamo. */
    List<PartidaCartaRow> buildPartidas(List<PartidaContrato> partidas) {
        return partidas.stream()
                .map(p -> new PartidaCartaRow(FormatoDocumento.descripcionPartida(p),
                        FormatoDocumento.money(p.getAvaluoContrato()),
                        FormatoDocumento.money(p.getMontoPrestamo())))
                .toList();
    }

    /** La carta nombra a la casa de empeño por su razón social; sin ella, por el nombre comercial. */
    private static String razonSocial(Empresa empresa) {
        if (empresa == null) {
            return "PRESTAMIL";
        }
        String nombre = empresa.getRazonSocial() != null && !empresa.getRazonSocial().isBlank()
                ? empresa.getRazonSocial() : empresa.getNombre();
        return nombre != null && !nombre.isBlank() ? nombre.trim().toUpperCase(Locale.ROOT) : "PRESTAMIL";
    }

    /**
     * Cómo se nombra en la carta cada tipo de identificación del avalúo, quién la expide y su género
     * gramatical ("A"/"O"). Las licencias las expide cada estado, por eso no llevan emisor.
     */
    record Identificacion(String nombre, String emisor, String genero) {

        static Identificacion de(String tipo) {
            if (tipo == null || tipo.isBlank()) {
                return new Identificacion("IDENTIFICACIÓN OFICIAL", null, "A");
            }
            String t = tipo.trim().toUpperCase(Locale.ROOT);
            return switch (t) {
                case "CREDENCIAL IFE" -> new Identificacion("CREDENCIAL PARA VOTAR",
                        "EL INSTITUTO FEDERAL ELECTORAL", "A");
                case "PASAPORTE" -> new Identificacion("PASAPORTE", "LA SECRETARÍA DE RELACIONES EXTERIORES", "O");
                case "CÉDULA PROFESIONAL" -> new Identificacion("CÉDULA PROFESIONAL",
                        "LA SECRETARÍA DE EDUCACIÓN PÚBLICA", "A");
                case "CARTILLA S.M.N." -> new Identificacion("CARTILLA DEL SERVICIO MILITAR NACIONAL",
                        "LA SECRETARÍA DE LA DEFENSA NACIONAL", "A");
                default -> new Identificacion(t, null, "A");
            };
        }
    }

    private synchronized JasperReport getReporte() {
        if (reporte == null) {
            try (InputStream is = new ClassPathResource(PLANTILLA).getInputStream()) {
                reporte = JasperCompileManager.compileReport(is);
            } catch (IOException | JRException e) {
                throw new BadRequestException("No se pudo cargar la plantilla de la carta responsiva: " + e.getMessage());
            }
        }
        return reporte;
    }

    private static String nz(String s) {
        return s != null ? s : "";
    }
}
