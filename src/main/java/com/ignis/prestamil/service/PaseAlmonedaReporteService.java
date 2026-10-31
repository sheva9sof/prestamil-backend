package com.ignis.prestamil.service;

import com.ignis.prestamil.model.Cliente;
import com.ignis.prestamil.model.Configuracion;
import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.Direccion;
import com.ignis.prestamil.model.PartidaContrato;
import com.ignis.prestamil.model.PaseAlmonedaDetalle;
import com.ignis.prestamil.model.Rol;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.model.TipoCambioPase;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.ConfiguracionRepository;
import com.ignis.prestamil.repository.PaseAlmonedaDetalleRepository;
import com.ignis.prestamil.repository.SucursalRepository;
import com.ignis.prestamil.repository.UsuarioRepository;
import com.ignis.prestamil.response.CarteraVencidaRow;
import com.ignis.prestamil.response.PaseAVentaRow;
import com.ignis.prestamil.response.ResultadosPaseAlmonedaResponse;
import com.ignis.prestamil.util.Constantes;
import com.ignis.prestamil.util.CsvReporte;
import com.ignis.prestamil.util.EncabezadoReporte;
import com.ignis.prestamil.util.FormatoDocumento;
import com.ignis.prestamil.util.ReporteJasper;
import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pantalla "Resultados del pase de almoneda" (C-11). Consulta por sucursal + fecha; devuelve dos
 * listas (cartera vencida y pase a venta) y las exporta a CSV (Excel) y a PDF (Jasper). Usa la
 * infraestructura común de reportes (REP-00): encabezado estándar, {@link ReporteJasper} y {@link CsvReporte}.
 */
@Service
@RequiredArgsConstructor
public class PaseAlmonedaReporteService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String PLANTILLA_CARTERA_VENCIDA = "jasper/pase-almoneda-cartera-vencida.jrxml";
    private static final String PLANTILLA_PASE_A_VENTA = "jasper/pase-almoneda-pase-a-venta.jrxml";

    private final PaseAlmonedaDetalleRepository detalleRepository;
    private final SucursalRepository sucursalRepository;
    private final UsuarioRepository usuarioRepository;
    private final ConfiguracionRepository configuracionRepository;
    private final Clock clock;

    /**
     * Resultados de un pase (C-11). Si no se encuentran filas se devuelven listas vacias; la UI
     * muestra entonces "sin movimientos". Si el usuario no puede elegir otra fecha (OTR-01) se
     * ignora la recibida y se consulta la de hoy del servidor.
     *
     * @param sucursalId      sucursal
     * @param fechaSolicitada fecha del selector de la pantalla (puede ser nula)
     * @param username        usuario que consulta
     * @return respuesta con las dos pestañas, la fecha realmente consultada y si puede cambiarla
     */
    @Transactional(readOnly = true)
    public ResultadosPaseAlmonedaResponse obtener(Integer sucursalId, LocalDate fechaSolicitada, String username) {
        boolean puedeElegirFecha = puedeElegirFecha(username);
        LocalDate fecha = puedeElegirFecha && fechaSolicitada != null ? fechaSolicitada : LocalDate.now(clock);
        return new ResultadosPaseAlmonedaResponse(fecha, puedeElegirFecha,
                carteraVencida(sucursalId, fecha), paseAVenta(sucursalId, fecha));
    }

    /**
     * Fecha que el usuario puede consultar o exportar (OTR-01): la solicitada si su rol está en
     * {@code ROLES_PERMITIDOS_PASE_ALMONEDA_OTRA_FECHA}; si no (o si no mandó fecha), la de hoy
     * del servidor. No responde 403: el cajero simplemente ve el pase del día.
     *
     * @param username        usuario que consulta
     * @param fechaSolicitada fecha recibida en la petición (puede ser nula)
     * @return fecha efectiva de la consulta
     */
    @Transactional(readOnly = true)
    public LocalDate fechaPermitida(String username, LocalDate fechaSolicitada) {
        if (fechaSolicitada != null && puedeElegirFecha(username)) {
            return fechaSolicitada;
        }
        return LocalDate.now(clock);
    }

    /**
     * Indica si el rol del usuario está en {@code ROLES_PERMITIDOS_PASE_ALMONEDA_OTRA_FECHA}.
     *
     * @param username usuario autenticado
     * @return {@code true} si puede consultar el pase de otra fecha
     */
    @Transactional(readOnly = true)
    public boolean puedeElegirFecha(String username) {
        Integer rolId = usuarioRepository.findByNombreUsuario(username)
                .map(Usuario::getRol)
                .map(Rol::getId)
                .orElse(null);
        return rolId != null && rolesPermitidosOtraFecha().contains(rolId);
    }

    /** CSV de ids de rol de la configuración; conjunto vacío = nadie elige otra fecha. */
    private Set<Integer> rolesPermitidosOtraFecha() {
        return configuracionRepository.findByConfiguracion(Constantes.ROLES_PERMITIDOS_PASE_ALMONEDA_OTRA_FECHA)
                .map(Configuracion::getValorCadena)
                .filter(csv -> csv != null && !csv.isBlank())
                .map(csv -> Arrays.stream(csv.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(Integer::parseInt)
                        .collect(Collectors.toSet()))
                .orElseGet(Collections::emptySet);
    }

    private List<CarteraVencidaRow> carteraVencida(Integer sucursalId, LocalDate fecha) {
        // TODO G-08: Jorge pidio que el contrato entre a "cartera vencida" el dia siguiente al
        // vencimiento, aunque siga en gracia ("en su dia 29"). Hoy el pase cambia el estatus
        // al rebasar la gracia (F11), asi que esta consulta refleja ese momento. Si se adopta
        // el criterio de Jorge, hay que ampliar la fuente para incluir contratos con
        // vencimiento = fecha - 1 aunque sigan en gracia.
        return detalleRepository
                .findByPaseSucursalIdAndPaseFechaAndTipoCambio(sucursalId, fecha, TipoCambioPase.VENCIDO)
                .stream()
                .map(this::toCarteraVencida)
                .sorted(Comparator.comparing(CarteraVencidaRow::folioContrato,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    private List<PaseAVentaRow> paseAVenta(Integer sucursalId, LocalDate fecha) {
        return detalleRepository
                .findByPaseSucursalIdAndPaseFechaAndTipoCambio(sucursalId, fecha, TipoCambioPase.EN_VENTA)
                .stream()
                .map(d -> toPaseAVenta(d, fecha))
                .sorted(Comparator
                        .comparing(PaseAVentaRow::folioContrato,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PaseAVentaRow::numPartida,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    /**
     * CSV con BOM UTF-8 para que Excel abra bien los acentos. Columnas en el mismo orden que la
     * pestaña de la pantalla. La fecha ya viene filtrada por {@link #fechaPermitida}.
     */
    @Transactional(readOnly = true)
    public byte[] exportarCarteraVencidaCsv(Integer sucursalId, LocalDate fecha) {
        List<CarteraVencidaRow> filas = carteraVencida(sucursalId, fecha);
        StringBuilder sb = new StringBuilder();
        sb.append("Contrato,Cliente,Direccion,Telefono,Vencimiento,Saldo\n");
        for (CarteraVencidaRow f : filas) {
            sb.append(CsvReporte.campo(f.folioContrato())).append(',')
                    .append(CsvReporte.campo(f.cliente())).append(',')
                    .append(CsvReporte.campo(f.direccion())).append(',')
                    .append(CsvReporte.campo(f.telefono())).append(',')
                    .append(CsvReporte.campo(f.fechaVencimiento() != null ? f.fechaVencimiento().toString() : "")).append(',')
                    .append(CsvReporte.campo(f.saldoCapital() != null ? f.saldoCapital().toPlainString() : ""))
                    .append('\n');
        }
        return CsvReporte.conBom(sb.toString());
    }

    @Transactional(readOnly = true)
    public byte[] exportarPaseAVentaCsv(Integer sucursalId, LocalDate fecha) {
        List<PaseAVentaRow> filas = paseAVenta(sucursalId, fecha);
        StringBuilder sb = new StringBuilder();
        sb.append("Contrato,Partida,Descripcion,Fecha de pase\n");
        for (PaseAVentaRow f : filas) {
            sb.append(CsvReporte.campo(f.folioContrato())).append(',')
                    .append(CsvReporte.campo(f.numPartida() != null ? f.numPartida().toString() : "")).append(',')
                    .append(CsvReporte.campo(f.descripcion())).append(',')
                    .append(CsvReporte.campo(f.fechaPase() != null ? f.fechaPase().toString() : ""))
                    .append('\n');
        }
        return CsvReporte.conBom(sb.toString());
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
        List<CarteraVencidaRow> filas = carteraVencida(sucursalId, fecha);
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
        return ReporteJasper.pdf(ReporteJasper.plantilla(PLANTILLA_CARTERA_VENCIDA), params,
                new JRMapCollectionDataSource(datos));
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
        List<PaseAVentaRow> filas = paseAVenta(sucursalId, fecha);
        Map<String, Object> params = parametrosPaseAVenta(sucursalId, fecha, filas);
        List<Map<String, ?>> datos = filas.stream()
                .<Map<String, ?>>map(f -> fila(
                        "contrato", f.folioContrato(),
                        "partida", f.numPartida() != null ? f.numPartida().toString() : null,
                        "descripcion", f.descripcion(),
                        "fechaPase", f.fechaPase() != null ? f.fechaPase().format(FECHA) : null))
                .toList();
        return ReporteJasper.pdf(ReporteJasper.plantilla(PLANTILLA_PASE_A_VENTA), params,
                new JRMapCollectionDataSource(datos));
    }

    /** Parámetros del PDF de cartera vencida. Package-private para verificar totales en tests. */
    Map<String, Object> parametrosCarteraVencida(Integer sucursalId, LocalDate fecha, List<CarteraVencidaRow> filas) {
        BigDecimal saldoTotal = filas.stream()
                .map(CarteraVencidaRow::saldoCapital)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> params = encabezado(sucursalId, fecha,
                "RESULTADOS DEL PASE DE ALMONEDA - CARTERA VENCIDA");
        params.put("P_TOTALES", "Contratos: " + filas.size() + "     Saldo total: " + FormatoDocumento.money(saldoTotal));
        params.put("P_SIN_DATOS", filas.isEmpty());
        return params;
    }

    /** Parámetros del PDF de pase a venta. Package-private para verificar totales en tests. */
    Map<String, Object> parametrosPaseAVenta(Integer sucursalId, LocalDate fecha, List<PaseAVentaRow> filas) {
        Map<String, Object> params = encabezado(sucursalId, fecha, "RESULTADOS DEL PASE DE ALMONEDA - PASE A VENTA");
        params.put("P_TOTALES", "Prendas: " + filas.size());
        params.put("P_SIN_DATOS", filas.isEmpty());
        return params;
    }

    /**
     * Encabezado estándar (REP-00) con los textos de siempre del pase: la sucursal con su domicilio y
     * "Fecha del pase: …" en la línea de fechas. Sin razón social ni código RPT, para no cambiar el PDF.
     * {@code P_FECHA_PASE} repite la fecha para el pie de página.
     */
    private Map<String, Object> encabezado(Integer sucursalId, LocalDate fecha, String titulo) {
        Sucursal sucursal = sucursalRepository.findById(sucursalId).orElse(null);
        String lineaSucursal = sucursal == null ? "" : "Sucursal "
                + (sucursal.getNumeroSucursal() != null ? sucursal.getNumeroSucursal() + " " : "")
                + nullSafe(sucursal.getNombre()) + " - " + FormatoDocumento.domicilio(sucursal);
        String fechaPase = "Fecha del pase: " + fecha.format(FECHA);
        Map<String, Object> params = new EncabezadoReporte(EncabezadoReporte.empresa(sucursal), null, lineaSucursal,
                titulo, fechaPase, EncabezadoReporte.impreso(LocalDateTime.now(clock)), null).parametros();
        params.put("P_FECHA_PASE", fechaPase);
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

    private CarteraVencidaRow toCarteraVencida(PaseAlmonedaDetalle detalle) {
        Contrato c = detalle.getContrato();
        Cliente cliente = c.getCliente();
        return CarteraVencidaRow.builder()
                .folioContrato(c.getFolio())
                .cliente(nombreCompleto(cliente))
                .direccion(direccionTexto(cliente != null ? cliente.getDireccion() : null))
                .telefono(cliente != null ? cliente.getTelefono() : null)
                // Valores del dia del pase (changeset 076); los del contrato solo si la fila no los trae
                .fechaVencimiento(detalle.getFechaVencimiento() != null
                        ? detalle.getFechaVencimiento() : c.getFechaVencimiento())
                .saldoCapital(detalle.getSaldoCapital() != null
                        ? detalle.getSaldoCapital() : c.getSaldoCapital())
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
}
