package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.model.Caja;
import com.ignis.prestamil.model.Denominacion;
import com.ignis.prestamil.model.Empresa;
import com.ignis.prestamil.model.EstatusTraspasoCaja;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.model.TipoTraspasoCaja;
import com.ignis.prestamil.model.TraspasoCaja;
import com.ignis.prestamil.model.TraspasoCajaDenominacion;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.DenominacionRepository;
import com.ignis.prestamil.repository.SucursalRepository;
import com.ignis.prestamil.repository.TraspasoCajaRepository;
import com.ignis.prestamil.util.DenominacionTicketRow;
import com.ignis.prestamil.util.FormatoDocumento;
import com.ignis.prestamil.util.LineaTicketRow;
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
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Ticket de traspaso entre cajas (CAJ-03, RC-04) en PDF de ~80 mm, por el mismo camino que
 * {@link TicketMovimientoService}: plantilla Jasper compilada una vez y PDF del ancho de la impresora
 * térmica. Todo sale de lo guardado en el traspaso; reimprimir solo cambia la fecha y hora de impresión.
 */
@Service
public class TicketTraspasoCajaService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final TraspasoCajaRepository traspasoCajaRepository;
    private final DenominacionRepository denominacionRepository;
    private final SucursalRepository sucursalRepository;
    private final Clock clock;

    private JasperReport reporte; // cacheado (la plantilla no cambia en runtime)

    public TicketTraspasoCajaService(TraspasoCajaRepository traspasoCajaRepository,
                                     DenominacionRepository denominacionRepository,
                                     SucursalRepository sucursalRepository,
                                     Clock clock) {
        this.traspasoCajaRepository = traspasoCajaRepository;
        this.denominacionRepository = denominacionRepository;
        this.sucursalRepository = sucursalRepository;
        this.clock = clock;
    }

    /**
     * Genera el ticket de un traspaso. Un traspaso cancelado también se imprime, con "CANCELADO" en grande.
     *
     * @param traspasoId identificador del traspaso
     * @return bytes del PDF
     * @throws ResourceNotFoundException si el traspaso no existe
     * @throws BadRequestException       si la plantilla no se puede cargar o llenar
     */
    @Transactional(readOnly = true)
    public byte[] generarPdf(Long traspasoId) {
        TraspasoCaja traspaso = traspasoCajaRepository.findById(traspasoId)
                .orElseThrow(() -> new ResourceNotFoundException("Traspaso no encontrado: " + traspasoId));
        try {
            JasperPrint print = JasperFillManager.fillReport(getReporte(), armarParametros(traspaso),
                    new JRBeanCollectionDataSource(renglones(traspaso)));
            return JasperExportManager.exportReportToPdf(print);
        } catch (JRException e) {
            throw new BadRequestException("No se pudo generar el ticket del traspaso: " + e.getMessage());
        }
    }

    /**
     * Parámetros de la plantilla. Package-private para verificar el contenido en tests sin leer el PDF.
     */
    Map<String, Object> armarParametros(TraspasoCaja traspaso) {
        Sucursal sucursal = sucursalRepository.findById(traspaso.getSucursalId()).orElse(null);
        Empresa empresa = sucursal != null ? sucursal.getEmpresa() : null;

        Map<String, Object> params = new HashMap<>();
        // Encabezado: fecha y hora de impresión, PRESTAMIL / razón social / sucursal
        params.put("P_FECHA_IMPRESION", "Impreso: " + LocalDateTime.now(clock).format(FECHA_HORA));
        params.put("P_EMPRESA", empresa != null && empresa.getNombre() != null
                ? empresa.getNombre().toUpperCase() : "PRESTAMIL");
        params.put("P_EMPRESA_DATOS", empresa != null ? empresa.getRazonSocial() : null);
        params.put("P_SUCURSAL", sucursal == null ? null : "Sucursal: "
                + (sucursal.getNumeroSucursal() != null ? sucursal.getNumeroSucursal() + " " : "")
                + nz(sucursal.getNombre()));
        params.put("P_TITULO", titulo(traspaso.getTipo()) + ": " + traspaso.getFolio());

        // Cancelado: la marca en grande y, debajo, cuándo, quién y por qué
        boolean cancelado = traspaso.getEstatus() == EstatusTraspasoCaja.CANCELADO;
        params.put("P_CANCELADO", cancelado);
        params.put("P_CANCELACION", cancelado ? lineas(
                "Cancelado el " + (traspaso.getFechaCancelacion() != null
                        ? traspaso.getFechaCancelacion().format(FECHA_HORA) : "")
                        + " por " + FormatoDocumento.nombreUsuario(traspaso.getUsuarioCancela()),
                "Motivo: " + nz(traspaso.getMotivoCancelacion())) : null);

        // Fecha de operación, cajas, usuario y su puesto; la referencia bancaria solo si se capturó
        Usuario usuario = traspaso.getUsuario();
        List<LineaTicketRow> datos = new ArrayList<>();
        datos.add(new LineaTicketRow("Fecha de operación", traspaso.getFechaOperacion().format(FECHA)));
        datos.add(new LineaTicketRow("Caja origen", caja(traspaso.getCajaOrigen())));
        datos.add(new LineaTicketRow("Caja destino", caja(traspaso.getCajaDestino())));
        datos.add(new LineaTicketRow("Usuario", FormatoDocumento.nombreUsuario(usuario)));
        datos.add(new LineaTicketRow("Puesto", usuario != null && usuario.getRol() != null
                ? nz(usuario.getRol().getRol()).toUpperCase() : ""));
        if (traspaso.getReferenciaBancaria() != null && !traspaso.getReferenciaBancaria().isBlank()) {
            datos.add(new LineaTicketRow("Referencia bancaria", traspaso.getReferenciaBancaria()));
        }
        params.put("P_DATOS", new JRBeanCollectionDataSource(datos));

        params.put("P_TOTAL", FormatoDocumento.money(traspaso.getImporte()));
        params.put("P_CAJA_ORIGEN", caja(traspaso.getCajaOrigen()));
        params.put("P_CAJA_DESTINO", caja(traspaso.getCajaDestino()));
        return params;
    }

    /**
     * Tabla Cantidad/Denominación/Importe: todas las denominaciones activas del catálogo, aunque estén en
     * 0, más las del desglose que se hayan desactivado después del traspaso. Ordenadas de mayor a menor.
     * Package-private para tests.
     */
    List<DenominacionTicketRow> renglones(TraspasoCaja traspaso) {
        Map<Integer, TraspasoCajaDenominacion> desglose = traspaso.getDesglose().stream()
                .collect(Collectors.toMap(d -> d.getDenominacion().getId(), d -> d));

        Map<Integer, Denominacion> denominaciones = new LinkedHashMap<>();
        denominacionRepository.findByActivaTrueOrderByOrdenAsc().forEach(d -> denominaciones.put(d.getId(), d));
        desglose.values().forEach(d -> denominaciones.putIfAbsent(d.getDenominacion().getId(), d.getDenominacion()));

        return denominaciones.values().stream()
                .sorted(Comparator.comparing(Denominacion::getOrden))
                .map(denominacion -> {
                    TraspasoCajaDenominacion renglon = desglose.get(denominacion.getId());
                    int cantidad = renglon != null ? renglon.getCantidad() : 0;
                    BigDecimal importe = renglon != null ? renglon.getImporte() : BigDecimal.ZERO;
                    return new DenominacionTicketRow(String.valueOf(cantidad),
                            FormatoDocumento.money(denominacion.getValor().setScale(2, RoundingMode.HALF_UP)),
                            FormatoDocumento.money(importe));
                })
                .toList();
    }

    private synchronized JasperReport getReporte() {
        if (reporte == null) {
            try (InputStream is = new ClassPathResource("jasper/ticket-traspaso-caja.jrxml").getInputStream()) {
                reporte = JasperCompileManager.compileReport(is);
            } catch (IOException | JRException e) {
                throw new BadRequestException("No se pudo cargar la plantilla del ticket de traspaso: "
                        + e.getMessage());
            }
        }
        return reporte;
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static String titulo(TipoTraspasoCaja tipo) {
        return switch (tipo) {
            case MINISTRACION -> "MINISTRACIÓN";
            case CONCENTRACION -> "CONCENTRACIÓN";
            case DOTACION -> "DOTACIÓN";
            case RECOLECCION -> "RECOLECCIÓN";
        };
    }

    /** "002 BÓVEDA". */
    private static String caja(Caja caja) {
        return caja == null ? "" : (nz(caja.getClave()) + " " + nz(caja.getNombre())).trim();
    }

    /** Une líneas no vacías con salto de línea: cada bloque del ticket es un solo campo que se estira. */
    private static String lineas(String... lineas) {
        return Stream.of(lineas)
                .filter(linea -> linea != null && !linea.isBlank())
                .collect(Collectors.joining("\n"));
    }

    private static String nz(String s) {
        return s != null ? s : "";
    }
}
