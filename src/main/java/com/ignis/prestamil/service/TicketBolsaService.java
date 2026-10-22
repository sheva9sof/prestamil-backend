package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.Empresa;
import com.ignis.prestamil.model.PartidaContrato;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.repository.ContratoRepository;
import com.ignis.prestamil.repository.SucursalRepository;
import com.ignis.prestamil.util.Constantes;
import com.ignis.prestamil.util.FormatoDocumento;
import com.ignis.prestamil.util.PartidaTicketRow;
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
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Ticket de bolsa (D-10): identifica en el almacén la bolsa donde se guardan las prendas del contrato.
 * PDF angosto (~80 mm) con el mismo ancho y estilo que la nota de movimiento (RN-25), para la impresora
 * térmica. Se imprime al generar el contrato (después del contrato y la carta responsiva) y se puede
 * reimprimir desde la consulta de partidas.
 */
@Service
public class TicketBolsaService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ContratoRepository contratoRepository;
    private final SucursalRepository sucursalRepository;

    private JasperReport reporte; // cacheado (la plantilla no cambia en runtime)

    public TicketBolsaService(ContratoRepository contratoRepository, SucursalRepository sucursalRepository) {
        this.contratoRepository = contratoRepository;
        this.sucursalRepository = sucursalRepository;
    }

    /**
     * Genera el ticket de bolsa de un contrato.
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
            throw new BadRequestException("No se pudo generar el ticket de bolsa: " + e.getMessage());
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
        List<PartidaContrato> partidas = contrato.getPartidas() != null ? contrato.getPartidas() : List.of();

        Map<String, Object> params = new HashMap<>();
        params.put("P_EMPRESA", empresa != null && empresa.getNombre() != null
                ? empresa.getNombre().toUpperCase() : "PRESTAMIL");
        params.put("P_SUCURSAL", sucursal == null ? "" : "Sucursal: "
                + (sucursal.getNumeroSucursal() != null ? sucursal.getNumeroSucursal() + " " : "")
                + nz(sucursal.getNombre()));
        params.put("P_CONTRATO", "Contrato: " + nz(contrato.getFolio()));
        params.put("P_BOLSA", contrato.getNumBolsa() != null ? contrato.getNumBolsa().toString() : "S/B");
        params.put("P_CLIENTE", contrato.getCliente() != null
                ? FormatoDocumento.nombreCompleto(contrato.getCliente()) : "");

        // Totales de la bolsa: piezas (no partidas: una partida puede agrupar varias piezas) y peso físico
        int piezas = partidas.stream()
                .map(PartidaContrato::getCantidad)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        params.put("P_PIEZAS", String.valueOf(piezas));
        params.put("P_PESO_TOTAL", pesoTotal(partidas));

        params.put("P_VALUADOR", FormatoDocumento.nombreUsuario(contrato.getUsuario()));
        params.put("P_FECHA_EMPENO", contrato.getFechaApertura() != null
                ? contrato.getFechaApertura().format(FECHA) : "");
        LocalDate comercializacion = contrato.getFechaComercializacion() != null
                ? contrato.getFechaComercializacion()
                : contrato.getFechaVencimiento() != null
                        ? contrato.getFechaVencimiento().plusDays(Constantes.DIAS_VENCIMIENTO_A_COMERCIALIZACION)
                        : null;
        params.put("P_FECHA_COMERCIALIZACION", comercializacion != null ? comercializacion.format(FECHA) : "");
        return params;
    }

    /** Filas de prendas: clave + descripción general y específica; debajo piezas, kilataje, peso y código. */
    List<PartidaTicketRow> buildPartidas(List<PartidaContrato> partidas) {
        List<PartidaTicketRow> filas = new ArrayList<>();
        for (PartidaContrato p : partidas) {
            String kilatajeHechura = Stream.of(p.getKilataje() != null ? p.getKilataje() + "K" : null, p.getHechura())
                    .filter(parte -> parte != null && !parte.isBlank())
                    .collect(Collectors.joining(" / "));
            BigDecimal peso = pesoPartida(p);
            String detalle = Stream.of(
                            p.getCantidad() != null ? p.getCantidad() + (p.getCantidad() == 1 ? " pza" : " pzas") : null,
                            kilatajeHechura.isEmpty() ? null : kilatajeHechura,
                            peso != null ? gramos(peso) + " g" : null,
                            p.getCodigoBarras() != null ? "Cód. " + p.getCodigoBarras() : null)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(" · "));
            filas.add(new PartidaTicketRow(String.valueOf(p.getNumPartida()),
                    FormatoDocumento.descripcionPartida(p), detalle));
        }
        return filas;
    }

    /**
     * Peso físico de la bolsa: suma del peso total de las piezas (con piedras), que es lo que pesa la
     * bolsa en el almacén; si una partida no tiene peso total se usa el neto. Varios y Autos no pesan.
     */
    private static String pesoTotal(List<PartidaContrato> partidas) {
        List<BigDecimal> pesos = partidas.stream()
                .map(TicketBolsaService::pesoPartida)
                .filter(Objects::nonNull)
                .toList();
        if (pesos.isEmpty()) {
            return "—";
        }
        return gramos(pesos.stream().reduce(BigDecimal.ZERO, BigDecimal::add)) + " g";
    }

    private static BigDecimal pesoPartida(PartidaContrato p) {
        return p.getPesoTotal() != null ? p.getPesoTotal() : p.getPesoNeto();
    }

    private synchronized JasperReport getReporte() {
        if (reporte == null) {
            try (InputStream is = new ClassPathResource("jasper/ticket-bolsa.jrxml").getInputStream()) {
                reporte = JasperCompileManager.compileReport(is);
            } catch (IOException | JRException e) {
                throw new BadRequestException("No se pudo cargar la plantilla del ticket de bolsa: " + e.getMessage());
            }
        }
        return reporte;
    }

    private static String gramos(BigDecimal valor) {
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US)).format(valor);
    }

    private static String nz(String s) {
        return s != null ? s : "";
    }
}
