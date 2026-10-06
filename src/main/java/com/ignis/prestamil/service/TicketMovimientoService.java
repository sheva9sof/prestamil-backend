package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.model.Cliente;
import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.Empresa;
import com.ignis.prestamil.model.MovimientoContrato;
import com.ignis.prestamil.model.PartidaContrato;
import com.ignis.prestamil.model.PlazoParametro;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.model.TipoMovimiento;
import com.ignis.prestamil.model.TipoTarjeta;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.MovimientoContratoRepository;
import com.ignis.prestamil.repository.PlazoParametroRepository;
import com.ignis.prestamil.repository.SucursalRepository;
import com.ignis.prestamil.service.calculo.CalculoContratoService;
import com.ignis.prestamil.service.calculo.ParametrosCalculo;
import com.ignis.prestamil.util.Constantes;
import com.ignis.prestamil.util.FormatoDocumento;
import com.ignis.prestamil.util.LineaTicketRow;
import com.ignis.prestamil.util.NumeroALetras;
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
 * Nota de movimiento (ticket) en PDF de ~80 mm, con el formato de la nota de COCAE (RN-25) más los datos
 * que COCAE no imprime: tipo de movimiento, periodos pagados, sanción, forma de pago, nuevo vencimiento y
 * fecha de comercialización. El PDF tiene el ancho de la impresora térmica para que el paso a ESC/POS sea
 * directo. Todo sale de lo guardado en el movimiento: reimprimir no recalcula.
 */
@Service
public class TicketMovimientoService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    // C-05: fecha larga para la línea grande de "NUEVO VENCIMIENTO" (ej. "28/septiembre/2026").
    private static final DateTimeFormatter FECHA_LARGA =
            DateTimeFormatter.ofPattern("dd/MMMM/yyyy", java.util.Locale.of("es", "MX"));

    private final MovimientoContratoRepository movimientoRepository;
    private final SucursalRepository sucursalRepository;
    private final PlazoParametroRepository plazoParametroRepository;
    private final CalculoContratoService calculoContratoService;

    private JasperReport reporte; // cacheado (la plantilla no cambia en runtime)
    private JasperReport reporteCancelacion; // cacheado (ticket C-06)
    private JasperReport reporteCancelacionMovimiento; // cacheado (ticket C-07)

    public TicketMovimientoService(MovimientoContratoRepository movimientoRepository,
                                   SucursalRepository sucursalRepository,
                                   PlazoParametroRepository plazoParametroRepository,
                                   CalculoContratoService calculoContratoService) {
        this.movimientoRepository = movimientoRepository;
        this.sucursalRepository = sucursalRepository;
        this.plazoParametroRepository = plazoParametroRepository;
        this.calculoContratoService = calculoContratoService;
    }

    /**
     * Genera el ticket de un movimiento.
     *
     * @param movimientoId identificador del movimiento
     * @return bytes del PDF
     * @throws ResourceNotFoundException si el movimiento no existe
     * @throws BadRequestException       si el movimiento es un empeño (su documento es el contrato)
     */
    @Transactional(readOnly = true)
    public byte[] generarPdf(Long movimientoId) {
        MovimientoContrato mov = movimientoRepository.findById(movimientoId)
                .orElseThrow(() -> new ResourceNotFoundException("Movimiento no encontrado: " + movimientoId));
        Map<String, Object> params = armarParametros(mov);
        try {
            JasperPrint print = JasperFillManager.fillReport(getReporte(), params,
                    new JRBeanCollectionDataSource(buildPartidas(mov.getContrato().getPartidas())));
            return JasperExportManager.exportReportToPdf(print);
        } catch (JRException e) {
            throw new BadRequestException("No se pudo generar el ticket del movimiento: " + e.getMessage());
        }
    }

    /**
     * Genera el ticket del último movimiento vigente (no cancelado) de un contrato (RN-22). El empeño no
     * cuenta: su documento es el contrato, no una nota. Reimprimir siempre trae el mismo ticket, sin
     * importar qué fila esté seleccionada en el historial.
     *
     * @param contratoId identificador del contrato
     * @return bytes del PDF de la nota vigente
     * @throws ResourceNotFoundException si el contrato no tiene un movimiento cobrado (todos cancelados,
     *                                   sin movimientos, o solo el empeño)
     */
    @Transactional(readOnly = true)
    public byte[] generarPdfVigente(Long contratoId) {
        MovimientoContrato mov = movimientoRepository
                .findFirstByContratoIdAndCanceladoFalseOrderByFechaDescIdDesc(contratoId)
                .filter(m -> m.getTipo() != TipoMovimiento.EMP)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No hay movimiento cobrado en el contrato " + contratoId));
        return generarPdf(mov.getId());
    }

    /**
     * Parámetros de la plantilla. Package-private para verificar el contenido en tests sin leer el PDF.
     */
    Map<String, Object> armarParametros(MovimientoContrato mov) {
        if (mov.getTipo() == TipoMovimiento.EMP) {
            throw new BadRequestException("El empeño no genera nota de movimiento; su documento es el contrato");
        }
        Contrato contrato = mov.getContrato();
        Sucursal sucursal = sucursalRepository.findById(contrato.getSucursalId()).orElse(null);
        Empresa empresa = sucursal != null ? sucursal.getEmpresa() : null;
        PlazoParametro parametro = parametroVigente(contrato);
        ParametrosCalculo p = calculoContratoService.resolverParametros(contrato, parametro);

        Map<String, Object> params = new HashMap<>();
        // Encabezado
        params.put("P_EMPRESA", empresa != null && empresa.getNombre() != null
                ? empresa.getNombre().toUpperCase() : "PRESTAMIL");
        params.put("P_EMPRESA_DATOS", lineas(
                empresa != null ? empresa.getRazonSocial() : null,
                FormatoDocumento.domicilioFiscal(empresa)));
        params.put("P_SUCURSAL", sucursal == null ? "" : lineas(
                "Expedido en la sucursal: " + (sucursal.getNumeroSucursal() != null ? sucursal.getNumeroSucursal() + " " : "")
                        + nz(sucursal.getNombre()),
                FormatoDocumento.domicilio(sucursal),
                sucursal.getTelefono() != null && !sucursal.getTelefono().isBlank() ? "Tel. " + sucursal.getTelefono() : null));
        params.put("P_FOLIO", "Folio No: " + (mov.getFolioNota() != null ? mov.getFolioNota() : "S/F"));
        params.put("P_FECHA", mov.getFecha().format(FECHA_HORA));
        params.put("P_TIPO_MOVIMIENTO", mov.getTipo().getEtiqueta().toUpperCase() + " (" + mov.getTipo() + ")");
        params.put("P_CANCELADO", Boolean.TRUE.equals(mov.getCancelado()));

        // Cliente y contrato
        Cliente cliente = contrato.getCliente();
        params.put("P_CONTRATO", "Contrato: " + nz(contrato.getFolio()));
        params.put("P_CLIENTE", cliente == null ? "" : lineas(
                "Cliente: " + cliente.getId() + " " + FormatoDocumento.nombreCompleto(cliente),
                FormatoDocumento.domicilio(cliente.getDireccion())));

        // Recuadro de parámetros (nota de COCAE)
        BigDecimal descuento = cero(mov.getImporteDescuento());
        params.put("P_PARAMETROS", renglones(
                linea("Avalúo", FormatoDocumento.money(contrato.getMontoAvaluo())),
                linea("Préstamo original", FormatoDocumento.money(contrato.getMontoPrestamo())),
                linea("Importe de abono", FormatoDocumento.money(mov.getAbonoCapital())),
                linea("Nuevo préstamo", FormatoDocumento.money(
                        mov.getSaldoNuevo() != null ? mov.getSaldoNuevo() : contrato.getSaldoCapital())),
                linea("% Intereses", porcentaje(p.porcInteres())),
                linea("% Almacenaje", porcentaje(p.porcAlmacen())),
                // El seguro no se cobra (GAP-09); se imprime como en COCAE
                linea("% Seguro", porcentaje(BigDecimal.ZERO)),
                linea("G.Oper. x Vta.", porcentaje(parametro != null ? parametro.getComisionPorVentaPrenda() : null)),
                linea("Moratorios %", porcentaje(p.aplicarSancion() ? p.porcSancionSemanal() : BigDecimal.ZERO)),
                linea("Desc. s/interés", porcentaje(mov.getPorcDescuentoInteres()) + "  "
                        + FormatoDocumento.money(descuento)),
                linea("% IVA", porcentaje(p.porcIva()))));

        // Datos del movimiento que COCAE no imprime
        List<LineaTicketRow> movimiento = new ArrayList<>();
        // La reposición es un ticket corto (RN-25): "REPOSICIÓN CONTRATO: n", sin periodos ni vencimiento
        if (mov.getTipo() == TipoMovimiento.RE) {
            movimiento.add(linea("REPOSICIÓN CONTRATO", nz(contrato.getFolio())));
        }
        if (mov.getPeriodosNormales() != null && mov.getTipo() != TipoMovimiento.RE) {
            movimiento.add(linea("Periodos pagados", mov.getPeriodosNormales() + " normales / "
                    + mov.getSemanasVencidas() + " extemp."));
        }
        if (mov.getDiasGraciaUsados() != null && mov.getDiasGraciaUsados() > 0) {
            movimiento.add(linea("Días de gracia usados", String.valueOf(mov.getDiasGraciaUsados())));
        }
        // C-05: la nueva fecha de vencimiento sale como línea grande al nivel del TOTAL (ver plantilla).
        // La "Comercialización" se queda en el bloque de movimiento con fuente normal.
        if (mov.getFechaVencNueva() != null && esRefrendo(mov.getTipo())) {
            movimiento.add(linea("Comercialización", mov.getFechaVencNueva()
                    .plusDays(Constantes.DIAS_VENCIMIENTO_A_COMERCIALIZACION).format(FECHA)));
        }
        params.put("P_MOVIMIENTO", new JRBeanCollectionDataSource(movimiento));
        params.put("P_NUEVO_VENCIMIENTO",
                mov.getFechaVencNueva() != null && esRefrendo(mov.getTipo())
                        ? mov.getFechaVencNueva().format(FECHA_LARGA)
                        : null);

        // Totales: subtotal = base del IVA; el abono y el capital van por fuera del IVA (RN-12).
        // Reposición (C-04): el monto fijo trae IVA desglosado — subtotal = total − IVA.
        List<LineaTicketRow> totales = new ArrayList<>();
        BigDecimal iva = cero(mov.getIva());
        if (mov.getTipo() == TipoMovimiento.RE) {
            BigDecimal totalRe = cero(mov.getMonto());
            BigDecimal subtotalRe = totalRe.subtract(iva);
            totales.add(linea("Subtotal", FormatoDocumento.money(subtotalRe)));
            totales.add(linea("IVA", FormatoDocumento.money(iva)));
        } else {
            BigDecimal interes = cero(mov.getInteres());
            BigDecimal sancion = cero(mov.getSancion());
            BigDecimal subtotal = interes.add(sancion).subtract(descuento);
            BigDecimal abono = cero(mov.getAbonoCapital());
            BigDecimal capital = cero(mov.getMonto()).subtract(subtotal).subtract(iva).subtract(abono);
            totales.add(linea("Intereses", FormatoDocumento.money(interes)));
            totales.add(linea("Sanción", FormatoDocumento.money(sancion)));
            if (descuento.signum() > 0) {
                totales.add(linea("Descuento", "-" + FormatoDocumento.money(descuento)));
            }
            totales.add(linea("Subtotal", FormatoDocumento.money(subtotal)));
            totales.add(linea("IVA", FormatoDocumento.money(iva)));
            if (abono.signum() > 0) {
                totales.add(linea("Abono a capital", FormatoDocumento.money(abono)));
            }
            if (capital.signum() > 0) {
                totales.add(linea("Capital", FormatoDocumento.money(capital)));
            }
        }
        params.put("P_TOTALES", new JRBeanCollectionDataSource(totales));
        params.put("P_TOTAL", FormatoDocumento.money(mov.getMonto()));

        // Pago: el total recibido en la ventana de Cobro, no el total del contrato (defecto de COCAE)
        params.put("P_PAGO", new JRBeanCollectionDataSource(pago(mov)));
        params.put("P_TOTAL_LETRA", "*** " + NumeroALetras.importe(cero(mov.getMonto())) + " ***");
        params.put("P_USUARIO", mov.getUsuario() != null ? nz(mov.getUsuario().getNombreUsuario()) : "");
        return params;
    }

    /**
     * Genera el comprobante de "CANCELACIÓN DE CONTRATO" para un empeño cancelado (C-06, RN-26).
     * Incluye los datos requeridos para la auditoría y la firma del cliente que recibe las prendas:
     * contrato, cliente, prendas devueltas, monto reingresado a caja, motivo, usuario, fecha y hora
     * y línea de firma. Si Jorge confirma que no se requiere firma (TODO G-09), basta con editar la
     * plantilla.
     *
     * @param movimientoId identificador del movimiento EMP cancelado
     * @return bytes del PDF
     * @throws ResourceNotFoundException si el movimiento no existe
     * @throws BadRequestException       si el movimiento no es un EMP cancelado
     */
    @Transactional(readOnly = true)
    public byte[] generarPdfCancelacionContrato(Long movimientoId) {
        MovimientoContrato mov = movimientoRepository.findById(movimientoId)
                .orElseThrow(() -> new ResourceNotFoundException("Movimiento no encontrado: " + movimientoId));
        if (mov.getTipo() != TipoMovimiento.EMP || !Boolean.TRUE.equals(mov.getCancelado())) {
            throw new BadRequestException(
                    "El comprobante de cancelación de contrato solo se genera para un empeño cancelado");
        }
        Map<String, Object> params = armarParametrosCancelacion(mov);
        try {
            JasperPrint print = JasperFillManager.fillReport(getReporteCancelacion(), params,
                    new JRBeanCollectionDataSource(buildPartidas(mov.getContrato().getPartidas())));
            return JasperExportManager.exportReportToPdf(print);
        } catch (JRException e) {
            throw new BadRequestException("No se pudo generar el comprobante de cancelación: " + e.getMessage());
        }
    }

    /** Parámetros del ticket de cancelación. Package-private para verificar su contenido en tests. */
    Map<String, Object> armarParametrosCancelacion(MovimientoContrato mov) {
        Contrato contrato = mov.getContrato();
        Sucursal sucursal = sucursalRepository.findById(contrato.getSucursalId()).orElse(null);
        Empresa empresa = sucursal != null ? sucursal.getEmpresa() : null;

        Map<String, Object> params = new HashMap<>();
        params.put("P_EMPRESA", empresa != null && empresa.getNombre() != null
                ? empresa.getNombre().toUpperCase() : "PRESTAMIL");
        params.put("P_EMPRESA_DATOS", lineas(
                empresa != null ? empresa.getRazonSocial() : null,
                FormatoDocumento.domicilioFiscal(empresa)));
        params.put("P_SUCURSAL", sucursal == null ? "" : lineas(
                "Expedido en la sucursal: " + (sucursal.getNumeroSucursal() != null
                        ? sucursal.getNumeroSucursal() + " " : "")
                        + nz(sucursal.getNombre()),
                FormatoDocumento.domicilio(sucursal),
                sucursal.getTelefono() != null && !sucursal.getTelefono().isBlank()
                        ? "Tel. " + sucursal.getTelefono() : null));
        // Fecha de la cancelación (no la del empeño): es la que firma el cliente al recibir las
        // prendas; si no se guardó, caemos a la fecha del movimiento.
        java.time.LocalDateTime cuando = mov.getFechaCancelacion() != null
                ? mov.getFechaCancelacion() : mov.getFecha();
        params.put("P_FECHA", cuando != null ? cuando.format(FECHA_HORA) : "");
        params.put("P_CONTRATO", "Contrato: " + nz(contrato.getFolio()));
        Cliente cliente = contrato.getCliente();
        params.put("P_CLIENTE", cliente == null ? "" : lineas(
                "Cliente: " + cliente.getId() + " " + FormatoDocumento.nombreCompleto(cliente),
                FormatoDocumento.domicilio(cliente.getDireccion())));

        BigDecimal monto = cero(mov.getMonto());
        params.put("P_MONTO", FormatoDocumento.money(monto));
        params.put("P_MONTO_LETRA", "*** " + NumeroALetras.importe(monto) + " ***");
        params.put("P_MOTIVO", nz(mov.getMotivoCancelacion()));
        Usuario usuarioCancela = mov.getUsuarioCancela();
        params.put("P_USUARIO", usuarioCancela != null ? nz(usuarioCancela.getNombreUsuario()) : "");
        return params;
    }

    /**
     * Dispatcher del comprobante de cancelación: EMP cancelado → cancelación de contrato (C-06);
     * cualquier otro cobro cancelado → cancelación de movimiento / devolución (C-07). Centraliza
     * la decisión para que el controlador no tenga que conocer los tipos.
     *
     * @param movimientoId identificador del movimiento cancelado
     * @return bytes del PDF del comprobante correspondiente
     */
    @Transactional(readOnly = true)
    public byte[] generarPdfCancelacion(Long movimientoId) {
        MovimientoContrato mov = movimientoRepository.findById(movimientoId)
                .orElseThrow(() -> new ResourceNotFoundException("Movimiento no encontrado: " + movimientoId));
        if (mov.getTipo() == TipoMovimiento.EMP) {
            return generarPdfCancelacionContrato(movimientoId);
        }
        return generarPdfCancelacionMovimiento(movimientoId);
    }

    /**
     * Genera el comprobante "CANCELACIÓN DE MOVIMIENTO / DEVOLUCIÓN" para un cobro cancelado (C-07).
     * Incluye tipo y folio del movimiento, contrato, cliente, monto devuelto en efectivo, desglose
     * de pago original (efectivo y tarjeta con últimos 4, banco y autorización), motivo, usuario
     * que canceló, fecha/hora y línea de firma del cliente. Aplica a cualquier movimiento con cobro
     * cancelado (RF, RPG, RC, RP, RPX, RX, FI, FX, RE); los EMP cancelados usan {@link #generarPdfCancelacionContrato}.
     *
     * @param movimientoId identificador del movimiento cancelado
     * @return bytes del PDF
     * @throws ResourceNotFoundException si el movimiento no existe
     * @throws BadRequestException       si el movimiento no está cancelado o es un empeño
     */
    @Transactional(readOnly = true)
    public byte[] generarPdfCancelacionMovimiento(Long movimientoId) {
        MovimientoContrato mov = movimientoRepository.findById(movimientoId)
                .orElseThrow(() -> new ResourceNotFoundException("Movimiento no encontrado: " + movimientoId));
        if (!Boolean.TRUE.equals(mov.getCancelado())) {
            throw new BadRequestException(
                    "El comprobante de cancelación de movimiento solo se genera para un movimiento cancelado");
        }
        if (mov.getTipo() == TipoMovimiento.EMP) {
            throw new BadRequestException(
                    "El empeño cancelado usa el comprobante de cancelación de contrato");
        }
        Map<String, Object> params = armarParametrosCancelacionMovimiento(mov);
        try {
            JasperPrint print = JasperFillManager.fillReport(getReporteCancelacionMovimiento(), params,
                    new JRBeanCollectionDataSource(List.of(new Object())));
            return JasperExportManager.exportReportToPdf(print);
        } catch (JRException e) {
            throw new BadRequestException(
                    "No se pudo generar el comprobante de cancelación: " + e.getMessage());
        }
    }

    /** Parámetros del ticket de cancelación de movimiento. Package-private para verificar en tests. */
    Map<String, Object> armarParametrosCancelacionMovimiento(MovimientoContrato mov) {
        Contrato contrato = mov.getContrato();
        Sucursal sucursal = sucursalRepository.findById(contrato.getSucursalId()).orElse(null);
        Empresa empresa = sucursal != null ? sucursal.getEmpresa() : null;

        Map<String, Object> params = new HashMap<>();
        params.put("P_EMPRESA", empresa != null && empresa.getNombre() != null
                ? empresa.getNombre().toUpperCase() : "PRESTAMIL");
        params.put("P_EMPRESA_DATOS", lineas(
                empresa != null ? empresa.getRazonSocial() : null,
                FormatoDocumento.domicilioFiscal(empresa)));
        params.put("P_SUCURSAL", sucursal == null ? "" : lineas(
                "Expedido en la sucursal: " + (sucursal.getNumeroSucursal() != null
                        ? sucursal.getNumeroSucursal() + " " : "")
                        + nz(sucursal.getNombre()),
                FormatoDocumento.domicilio(sucursal),
                sucursal.getTelefono() != null && !sucursal.getTelefono().isBlank()
                        ? "Tel. " + sucursal.getTelefono() : null));
        java.time.LocalDateTime cuando = mov.getFechaCancelacion() != null
                ? mov.getFechaCancelacion() : mov.getFecha();
        params.put("P_FECHA", cuando != null ? cuando.format(FECHA_HORA) : "");
        params.put("P_TIPO_MOVIMIENTO", "MOVIMIENTO: "
                + (mov.getTipo() != null ? mov.getTipo().getEtiqueta().toUpperCase() : "")
                + " (" + mov.getTipo() + ")");
        params.put("P_FOLIO_NOTA", "Folio nota: "
                + (mov.getFolioNota() != null ? mov.getFolioNota() : "S/F"));
        params.put("P_CONTRATO", "Contrato: " + nz(contrato.getFolio()));
        Cliente cliente = contrato.getCliente();
        params.put("P_CLIENTE", cliente == null ? "" : lineas(
                "Cliente: " + cliente.getId() + " " + FormatoDocumento.nombreCompleto(cliente),
                FormatoDocumento.domicilio(cliente.getDireccion())));

        BigDecimal total = cero(mov.getMonto());
        params.put("P_MONTO", FormatoDocumento.money(total));
        params.put("P_MONTO_LETRA", "*** " + NumeroALetras.importe(total) + " ***");

        BigDecimal efectivo = cero(mov.getImporteEfectivo());
        BigDecimal tarjeta = cero(mov.getImporteTarjeta());
        StringBuilder original = new StringBuilder();
        if (efectivo.signum() > 0) {
            original.append("Efectivo: ").append(FormatoDocumento.money(efectivo));
        }
        if (tarjeta.signum() > 0) {
            if (original.length() > 0) original.append("\n");
            original.append("Tarjeta: ").append(FormatoDocumento.money(tarjeta));
        }
        if (original.length() == 0) {
            original.append("Efectivo: ").append(FormatoDocumento.money(total));
        }
        params.put("P_PAGO_ORIGINAL", original.toString());

        if (tarjeta.signum() > 0) {
            String tipo = mov.getTipoTarjeta() == TipoTarjeta.CREDITO ? "crédito" : "débito";
            StringBuilder t = new StringBuilder("DEVOLUCIÓN DE PAGO CON TARJETA\n");
            t.append("Tarjeta ").append(tipo).append(" ****").append(nz(mov.getTarjetaUltimos4())).append("\n");
            if (mov.getBancoEmisor() != null) {
                t.append("Banco: ").append(mov.getBancoEmisor().getNombre()).append("\n");
            }
            t.append("Autorización: ").append(nz(mov.getAutorizacionBanco()));
            params.put("P_TARJETA", t.toString());
        } else {
            params.put("P_TARJETA", null);
        }

        params.put("P_MOTIVO", nz(mov.getMotivoCancelacion()));
        Usuario usuarioCancela = mov.getUsuarioCancela();
        params.put("P_USUARIO", usuarioCancela != null ? nz(usuarioCancela.getNombreUsuario()) : "");
        return params;
    }

    private synchronized JasperReport getReporteCancelacionMovimiento() {
        if (reporteCancelacionMovimiento == null) {
            try (InputStream is = new ClassPathResource(
                    "jasper/ticket-cancelacion-movimiento.jrxml").getInputStream()) {
                reporteCancelacionMovimiento = JasperCompileManager.compileReport(is);
            } catch (IOException | JRException e) {
                throw new BadRequestException(
                        "No se pudo cargar la plantilla del ticket de cancelación de movimiento: "
                                + e.getMessage());
            }
        }
        return reporteCancelacionMovimiento;
    }

    private synchronized JasperReport getReporteCancelacion() {
        if (reporteCancelacion == null) {
            try (InputStream is = new ClassPathResource(
                    "jasper/ticket-cancelacion-contrato.jrxml").getInputStream()) {
                reporteCancelacion = JasperCompileManager.compileReport(is);
            } catch (IOException | JRException e) {
                throw new BadRequestException(
                        "No se pudo cargar la plantilla del ticket de cancelación: " + e.getMessage());
            }
        }
        return reporteCancelacion;
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private List<LineaTicketRow> pago(MovimientoContrato mov) {
        List<LineaTicketRow> filas = new ArrayList<>();
        // Movimientos anteriores a la ventana de Cobro no guardaron la forma de pago
        if (mov.getImporteEfectivo() == null && mov.getImporteTarjeta() == null) {
            return filas;
        }
        BigDecimal efectivo = cero(mov.getImporteEfectivo());
        BigDecimal tarjeta = cero(mov.getImporteTarjeta());
        if (efectivo.signum() > 0) {
            filas.add(linea("Efectivo", FormatoDocumento.money(efectivo)));
        }
        if (tarjeta.signum() > 0) {
            String tipo = mov.getTipoTarjeta() == TipoTarjeta.CREDITO ? "crédito" : "débito";
            filas.add(linea("Tarjeta de " + tipo + " ****" + nz(mov.getTarjetaUltimos4()),
                    FormatoDocumento.money(tarjeta)));
            filas.add(linea("Banco", mov.getBancoEmisor() != null ? mov.getBancoEmisor().getNombre() : ""));
            filas.add(linea("Autorización", nz(mov.getAutorizacionBanco())));
        }
        filas.add(linea("Pago recibido", FormatoDocumento.money(efectivo.add(tarjeta))));
        filas.add(linea("Cambio", FormatoDocumento.money(mov.getCambioEntregado())));
        return filas;
    }

    private static LineaTicketRow linea(String etiqueta, String valor) {
        return new LineaTicketRow(etiqueta, valor);
    }

    private static JRBeanCollectionDataSource renglones(LineaTicketRow... lineas) {
        return new JRBeanCollectionDataSource(List.of(lineas));
    }

    private List<PartidaTicketRow> buildPartidas(List<PartidaContrato> partidas) {
        List<PartidaTicketRow> filas = new ArrayList<>();
        for (PartidaContrato p : partidas) {
            String descripcion = Stream.of(p.getClavePrenda(), p.getDescripcion())
                    .filter(parte -> parte != null && !parte.isBlank())
                    .collect(Collectors.joining(" "));
            String kilatajeHechura = Stream.of(p.getKilataje() != null ? p.getKilataje() + "K" : null, p.getHechura())
                    .filter(parte -> parte != null && !parte.isBlank())
                    .collect(Collectors.joining(" / "));
            String detalle = Stream.of(
                            p.getTipoPrenda() != null ? p.getTipoPrenda().getTipo() : null,
                            kilatajeHechura.isEmpty() ? null : kilatajeHechura,
                            p.getPesoNeto() != null ? gramos(p.getPesoNeto()) + " g" : null)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(" · "));
            filas.add(new PartidaTicketRow(String.valueOf(p.getNumPartida()), descripcion, detalle));
        }
        return filas;
    }

    private static boolean esRefrendo(TipoMovimiento tipo) {
        return tipo != TipoMovimiento.FI && tipo != TipoMovimiento.FX && tipo != TipoMovimiento.RE;
    }

    /** PlazoParametro vigente (plazo + tipo de la primera partida + sucursal), mismo criterio que el contrato. */
    private PlazoParametro parametroVigente(Contrato contrato) {
        List<PartidaContrato> partidas = contrato.getPartidas();
        if (partidas == null || partidas.isEmpty() || partidas.get(0).getTipoPrenda() == null) {
            return null;
        }
        return plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(
                        contrato.getPlazo().getId(), partidas.get(0).getTipoPrenda().getId(), contrato.getSucursalId())
                .orElse(null);
    }

    private synchronized JasperReport getReporte() {
        if (reporte == null) {
            try (InputStream is = new ClassPathResource("jasper/ticket-movimiento.jrxml").getInputStream()) {
                reporte = JasperCompileManager.compileReport(is);
            } catch (IOException | JRException e) {
                throw new BadRequestException("No se pudo cargar la plantilla del ticket: " + e.getMessage());
            }
        }
        return reporte;
    }

    /** Une líneas no vacías con salto de línea: cada bloque del ticket es un solo campo que se estira. */
    private static String lineas(String... lineas) {
        return Stream.of(lineas)
                .filter(linea -> linea != null && !linea.isBlank())
                .collect(Collectors.joining("\n"));
    }

    private static String porcentaje(BigDecimal valor) {
        return formato("0.00").format(valor != null ? valor : BigDecimal.ZERO) + " %";
    }

    private static String gramos(BigDecimal valor) {
        return formato("#,##0.00").format(valor);
    }

    private static DecimalFormat formato(String patron) {
        return new DecimalFormat(patron, DecimalFormatSymbols.getInstance(Locale.US));
    }

    private static BigDecimal cero(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }

    private static String nz(String s) {
        return s != null ? s : "";
    }
}
