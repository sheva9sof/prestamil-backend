package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.model.Cliente;
import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.Direccion;
import com.ignis.prestamil.model.Empresa;
import com.ignis.prestamil.model.PartidaContrato;
import com.ignis.prestamil.model.PaseAlmonedaDetalle;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.model.TipoCambioPase;
import com.ignis.prestamil.repository.PaseAlmonedaDetalleRepository;
import com.ignis.prestamil.repository.SucursalRepository;
import com.ignis.prestamil.response.CarteraVencidaRow;
import com.ignis.prestamil.response.PaseAVentaRow;
import com.ignis.prestamil.response.ResultadosPaseAlmonedaResponse;
import com.ignis.prestamil.util.FormatoDocumento;
import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Pantalla "Resultados del pase de almoneda" (C-11). Consulta por sucursal + fecha; devuelve dos
 * listas (cartera vencida y pase a venta) y las exporta a CSV (Excel) y a PDF (Jasper).
 */
@Service
@RequiredArgsConstructor
public class PaseAlmonedaReporteService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final PaseAlmonedaDetalleRepository detalleRepository;
    private final SucursalRepository sucursalRepository;

    private JasperReport reporteCarteraVencida; // cacheado (la plantilla no cambia en runtime)
    private JasperReport reportePaseAVenta; // cacheado

    /**
     * Resultados de un pase (C-11). Si no se encuentran filas se devuelven listas vacias; la UI
     * muestra entonces "sin movimientos".
     *
     * @param sucursalId sucursal
     * @param fecha      fecha del pase (del selector de la pantalla)
     * @return respuesta con las dos pestañas
     */
    @Transactional(readOnly = true)
    public ResultadosPaseAlmonedaResponse obtener(Integer sucursalId, LocalDate fecha) {
        // TODO G-08: Jorge pidio que el contrato entre a "cartera vencida" el dia siguiente al
        // vencimiento, aunque siga en gracia ("en su dia 29"). Hoy el pase cambia el estatus
        // al rebasar la gracia (F11), asi que esta consulta refleja ese momento. Si se adopta
        // el criterio de Jorge, hay que ampliar la fuente para incluir contratos con
        // vencimiento = fecha - 1 aunque sigan en gracia.
        List<CarteraVencidaRow> cartera = detalleRepository
                .findByPaseSucursalIdAndPaseFechaAndTipoCambio(sucursalId, fecha, TipoCambioPase.VENCIDO)
                .stream()
                .map(this::toCarteraVencida)
                .sorted(Comparator.comparing(CarteraVencidaRow::folioContrato,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
        List<PaseAVentaRow> venta = detalleRepository
                .findByPaseSucursalIdAndPaseFechaAndTipoCambio(sucursalId, fecha, TipoCambioPase.EN_VENTA)
                .stream()
                .map(d -> toPaseAVenta(d, fecha))
                .sorted(Comparator
                        .comparing(PaseAVentaRow::folioContrato,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PaseAVentaRow::numPartida,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
        return new ResultadosPaseAlmonedaResponse(fecha, cartera, venta);
    }

    /**
     * CSV con BOM UTF-8 para que Excel abra bien los acentos. Columnas en el mismo orden que la
     * pestaña de la pantalla.
     */
    public byte[] exportarCarteraVencidaCsv(Integer sucursalId, LocalDate fecha) {
        List<CarteraVencidaRow> filas = obtener(sucursalId, fecha).carteraVencida();
        StringBuilder sb = new StringBuilder();
        sb.append("Contrato,Cliente,Direccion,Telefono,Vencimiento,Saldo\n");
        for (CarteraVencidaRow f : filas) {
            sb.append(csv(f.folioContrato())).append(',')
                    .append(csv(f.cliente())).append(',')
                    .append(csv(f.direccion())).append(',')
                    .append(csv(f.telefono())).append(',')
                    .append(csv(f.fechaVencimiento() != null ? f.fechaVencimiento().toString() : "")).append(',')
                    .append(csv(f.saldoCapital() != null ? f.saldoCapital().toPlainString() : ""))
                    .append('\n');
        }
        return conBom(sb.toString());
    }

    public byte[] exportarPaseAVentaCsv(Integer sucursalId, LocalDate fecha) {
        List<PaseAVentaRow> filas = obtener(sucursalId, fecha).paseAVenta();
        StringBuilder sb = new StringBuilder();
        sb.append("Contrato,Partida,Descripcion,Fecha de pase\n");
        for (PaseAVentaRow f : filas) {
            sb.append(csv(f.folioContrato())).append(',')
                    .append(csv(f.numPartida() != null ? f.numPartida().toString() : "")).append(',')
                    .append(csv(f.descripcion())).append(',')
                    .append(csv(f.fechaPase() != null ? f.fechaPase().toString() : ""))
                    .append('\n');
        }
        return conBom(sb.toString());
    }

    /**
     * PDF de la pestaña "Cartera vencida": mismas columnas que la pantalla y el CSV, con encabezado
     * de empresa/sucursal y total de contratos y saldo al final.
     *
     * @param sucursalId sucursal
     * @param fecha      fecha del pase
     * @return bytes del PDF (carta horizontal)
     */
    @Transactional(readOnly = true)
    public byte[] exportarCarteraVencidaPdf(Integer sucursalId, LocalDate fecha) {
        List<CarteraVencidaRow> filas = obtener(sucursalId, fecha).carteraVencida();
        Map<String, Object> params = parametrosCarteraVencida(sucursalId, fecha, filas);
        List<Map<String, ?>> datos = filas.stream()
                .<Map<String, ?>>map(f -> fila(
                        "contrato", f.folioContrato(),
                        "cliente", f.cliente(),
                        "direccion", f.direccion(),
                        "telefono", f.telefono(),
                        "vencimiento", f.fechaVencimiento() != null ? f.fechaVencimiento().format(FECHA) : null,
                        "saldo", FormatoDocumento.money(f.saldoCapital())))
                .toList();
        return generarPdf(getReporteCarteraVencida(), params, datos);
    }

    /**
     * PDF de la pestaña "Pase a venta": mismas columnas que la pantalla y el CSV, con encabezado de
     * empresa/sucursal y total de prendas al final.
     *
     * @param sucursalId sucursal
     * @param fecha      fecha del pase
     * @return bytes del PDF (carta vertical)
     */
    @Transactional(readOnly = true)
    public byte[] exportarPaseAVentaPdf(Integer sucursalId, LocalDate fecha) {
        List<PaseAVentaRow> filas = obtener(sucursalId, fecha).paseAVenta();
        Map<String, Object> params = parametrosPaseAVenta(sucursalId, fecha, filas);
        List<Map<String, ?>> datos = filas.stream()
                .<Map<String, ?>>map(f -> fila(
                        "contrato", f.folioContrato(),
                        "partida", f.numPartida() != null ? f.numPartida().toString() : null,
                        "descripcion", f.descripcion(),
                        "fechaPase", f.fechaPase() != null ? f.fechaPase().format(FECHA) : null))
                .toList();
        return generarPdf(getReportePaseAVenta(), params, datos);
    }

    /** Parámetros del PDF de cartera vencida. Package-private para verificar totales en tests. */
    Map<String, Object> parametrosCarteraVencida(Integer sucursalId, LocalDate fecha, List<CarteraVencidaRow> filas) {
        BigDecimal saldoTotal = filas.stream()
                .map(CarteraVencidaRow::saldoCapital)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> params = encabezado(sucursalId, fecha);
        params.put("P_TOTALES", "Contratos: " + filas.size() + "     Saldo total: " + FormatoDocumento.money(saldoTotal));
        params.put("P_SIN_DATOS", filas.isEmpty());
        return params;
    }

    /** Parámetros del PDF de pase a venta. Package-private para verificar totales en tests. */
    Map<String, Object> parametrosPaseAVenta(Integer sucursalId, LocalDate fecha, List<PaseAVentaRow> filas) {
        Map<String, Object> params = encabezado(sucursalId, fecha);
        params.put("P_TOTALES", "Prendas: " + filas.size());
        params.put("P_SIN_DATOS", filas.isEmpty());
        return params;
    }

    /** Encabezado común: empresa, sucursal, fecha del pase y fecha de impresión. */
    private Map<String, Object> encabezado(Integer sucursalId, LocalDate fecha) {
        Sucursal sucursal = sucursalRepository.findById(sucursalId).orElse(null);
        Empresa empresa = sucursal != null ? sucursal.getEmpresa() : null;
        Map<String, Object> params = new HashMap<>();
        params.put("P_EMPRESA", empresa != null && empresa.getNombre() != null
                ? empresa.getNombre().toUpperCase() : "PRESTAMIL");
        params.put("P_SUCURSAL", sucursal == null ? "" : "Sucursal "
                + (sucursal.getNumeroSucursal() != null ? sucursal.getNumeroSucursal() + " " : "")
                + nullSafe(sucursal.getNombre()) + " - " + FormatoDocumento.domicilio(sucursal));
        params.put("P_FECHA_PASE", "Fecha del pase: " + fecha.format(FECHA));
        params.put("P_IMPRESO", "Impreso: " + LocalDateTime.now().format(FECHA_HORA));
        return params;
    }

    /** Fila del data source de Jasper a partir de pares clave/valor; admite valores nulos. */
    private static Map<String, ?> fila(String... clavesYValores) {
        Map<String, String> fila = new HashMap<>();
        for (int i = 0; i < clavesYValores.length; i += 2) {
            fila.put(clavesYValores[i], clavesYValores[i + 1]);
        }
        return fila;
    }

    private byte[] generarPdf(JasperReport reporte, Map<String, Object> params, List<Map<String, ?>> datos) {
        try {
            JasperPrint print = JasperFillManager.fillReport(reporte, params, new JRMapCollectionDataSource(datos));
            return JasperExportManager.exportReportToPdf(print);
        } catch (JRException e) {
            throw new BadRequestException("No se pudo generar el PDF del pase de almoneda: " + e.getMessage());
        }
    }

    private synchronized JasperReport getReporteCarteraVencida() {
        if (reporteCarteraVencida == null) {
            reporteCarteraVencida = compilar("jasper/pase-almoneda-cartera-vencida.jrxml");
        }
        return reporteCarteraVencida;
    }

    private synchronized JasperReport getReportePaseAVenta() {
        if (reportePaseAVenta == null) {
            reportePaseAVenta = compilar("jasper/pase-almoneda-pase-a-venta.jrxml");
        }
        return reportePaseAVenta;
    }

    private JasperReport compilar(String ruta) {
        try (InputStream is = new ClassPathResource(ruta).getInputStream()) {
            return JasperCompileManager.compileReport(is);
        } catch (IOException | JRException e) {
            throw new BadRequestException("No se pudo cargar la plantilla del pase de almoneda: " + e.getMessage());
        }
    }

    private CarteraVencidaRow toCarteraVencida(PaseAlmonedaDetalle detalle) {
        Contrato c = detalle.getContrato();
        Cliente cliente = c.getCliente();
        return CarteraVencidaRow.builder()
                .folioContrato(c.getFolio())
                .cliente(nombreCompleto(cliente))
                .direccion(direccionTexto(cliente != null ? cliente.getDireccion() : null))
                .telefono(cliente != null ? cliente.getTelefono() : null)
                .fechaVencimiento(c.getFechaVencimiento())
                .saldoCapital(c.getSaldoCapital())
                .build();
    }

    private PaseAVentaRow toPaseAVenta(PaseAlmonedaDetalle detalle, LocalDate fecha) {
        PartidaContrato partida = detalle.getPartida();
        Contrato c = detalle.getContrato();
        return PaseAVentaRow.builder()
                .folioContrato(c.getFolio())
                .numPartida(partida != null ? partida.getNumPartida() : null)
                .descripcion(partida != null ? partida.getDescripcion() : null)
                .fechaPase(fecha)
                .build();
    }

    private String nombreCompleto(Cliente c) {
        if (c == null) {
            return "";
        }
        return String.join(" ",
                nullSafe(c.getNombre()),
                nullSafe(c.getApellidoPaterno()),
                nullSafe(c.getApellidoMaterno())).trim().replaceAll(" +", " ");
    }

    private String direccionTexto(Direccion d) {
        if (d == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(nullSafe(d.getCalle()));
        if (d.getNumeroExterior() != null && !d.getNumeroExterior().isBlank()) {
            sb.append(" ").append(d.getNumeroExterior());
        }
        if (d.getNumeroInterior() != null && !d.getNumeroInterior().isBlank()) {
            sb.append(" int. ").append(d.getNumeroInterior());
        }
        if (d.getColonia() != null && !d.getColonia().isBlank()) {
            sb.append(", col. ").append(d.getColonia());
        }
        if (d.getCiudad() != null && !d.getCiudad().isBlank()) {
            sb.append(", ").append(d.getCiudad());
        }
        if (d.getEstado() != null && !d.getEstado().isBlank()) {
            sb.append(", ").append(d.getEstado());
        }
        if (d.getCodigoPostal() != null && !d.getCodigoPostal().isBlank()) {
            sb.append(" C.P. ").append(d.getCodigoPostal());
        }
        return sb.toString();
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }

    /** Escapa un campo CSV: comillas dobles si contiene coma, comilla o salto de linea. */
    private String csv(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        boolean needQuotes = s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0;
        String escaped = s.replace("\"", "\"\"");
        return needQuotes ? "\"" + escaped + "\"" : escaped;
    }

    /** Antepone el BOM UTF-8 para que Excel detecte el encoding al abrir el .csv. */
    private byte[] conBom(String contenido) {
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = contenido.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, out, 0, bom.length);
        System.arraycopy(body, 0, out, bom.length, body.length);
        return out;
    }
}
