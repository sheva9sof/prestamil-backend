package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.model.Cliente;
import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.Direccion;
import com.ignis.prestamil.model.PartidaContrato;
import com.ignis.prestamil.model.Plazo;
import com.ignis.prestamil.model.PlazoParametro;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.repository.PlazoParametroRepository;
import com.ignis.prestamil.repository.SucursalRepository;
import com.ignis.prestamil.response.VencimientoResponse;
import com.ignis.prestamil.util.PagoExtemporaneoRow;
import com.ignis.prestamil.util.PagoRow;
import com.ignis.prestamil.util.PrendaRow;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.JasperCompileManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Genera el PDF del contrato de mutuo con JasperReports a partir de la plantilla
 * {@code jasper/contrato.jasper}. El cálculo de vencimientos se reutiliza de
 * {@link ContratoService#calcularAmortizacion(Long)} para que el PDF coincida con
 * la tabla de amortización de la pantalla de Avalúos y con COCAE.
 */
@Service
public class ContratoPdfService {

    private static final BigDecimal IVA_PORCENTAJE = new BigDecimal("16");
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String[] MESES_ABR = {"ene", "feb", "mar", "abr", "may", "jun",
            "jul", "ago", "sep", "oct", "nov", "dic"};
    private final DecimalFormat MONEY = new DecimalFormat("#,##0.00");

    private final ContratoService contratoService;
    private final PlazoParametroRepository plazoParametroRepository;
    private final SucursalRepository sucursalRepository;

    private JasperReport reporte; // cacheado (la plantilla no cambia en runtime)

    public ContratoPdfService(ContratoService contratoService,
                              PlazoParametroRepository plazoParametroRepository,
                              SucursalRepository sucursalRepository) {
        this.contratoService = contratoService;
        this.plazoParametroRepository = plazoParametroRepository;
        this.sucursalRepository = sucursalRepository;
    }

    /**
     * Genera el PDF del contrato indicado.
     * @param contratoId id del contrato ya guardado
     * @return bytes del PDF
     */
    @Transactional(readOnly = true)
    public byte[] generarPdf(Long contratoId) {
        Contrato contrato = contratoService.findById(contratoId);
        List<PartidaContrato> partidas = contrato.getPartidas();
        if (partidas == null || partidas.isEmpty()) {
            throw new BadRequestException("El contrato no tiene partidas; no se puede generar el PDF");
        }
        Plazo plazo = contrato.getPlazo();
        Integer sucursalId = contrato.getSucursalId();
        PartidaContrato primera = partidas.get(0);

        PlazoParametro parametro = plazoParametroRepository
                .findByPlazoIdAndTipoPrendaIdAndSucursalId(
                        plazo.getId(), primera.getTipoPrenda().getId(), sucursalId)
                .orElse(null);
        Sucursal sucursal = sucursalId != null
                ? sucursalRepository.findById(sucursalId).orElse(null) : null;

        BigDecimal porcInteres  = valOr(parametro != null ? parametro.getPorcInteres() : null);
        BigDecimal porcAlmacen  = valOr(parametro != null ? parametro.getPorcAlmacen() : null);
        BigDecimal porcGastos   = valOr(parametro != null ? parametro.getPorcGastosAdmin() : null);
        BigDecimal porcTotal    = porcInteres.add(porcAlmacen).add(porcGastos);
        BigDecimal porcSancion  = valOr(parametro != null ? parametro.getPorcSancionSemanal() : null);
        BigDecimal comisionVenta = valOr(parametro != null ? parametro.getComisionPorVentaPrenda() : null);

        // Tasa anual y CAT como los muestra COCAE (interés/CAT anualizados: % x 360/díasPorPeriodo)
        BigDecimal factorAnual = new BigDecimal(360)
                .divide(new BigDecimal(Math.max(plazo.getDiasPorPeriodo(), 1)), 6, RoundingMode.HALF_UP);
        BigDecimal tasaAnual = porcInteres.multiply(factorAnual).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cat       = porcTotal.multiply(factorAnual).setScale(2, RoundingMode.HALF_UP);

        // Vencimientos (mismo cálculo que la pantalla de Avalúos y COCAE)
        List<VencimientoResponse> vencimientos = contratoService.calcularAmortizacion(contratoId);
        VencimientoResponse ultimo = vencimientos.get(vencimientos.size() - 1);
        BigDecimal montoTotalPagar = ultimo.getDesempeno();
        BigDecimal refrendoFinal = valOr(ultimo.getTotalInteres()).add(valOr(ultimo.getIva()));

        BigDecimal pesoTotal = BigDecimal.ZERO;
        for (PartidaContrato p : partidas) pesoTotal = pesoTotal.add(valOr(p.getPesoGramos()));

        BigDecimal porcPrestSobreAvaluo = contrato.getMontoAvaluo() != null
                && contrato.getMontoAvaluo().compareTo(BigDecimal.ZERO) > 0
                ? contrato.getMontoPrestamo().multiply(new BigDecimal(100))
                    .divide(contrato.getMontoAvaluo(), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<String, Object> params = new HashMap<>();
        params.put("P_CLIENTE", nombreCliente(contrato.getCliente()));
        params.put("P_COTITULAR", "");
        params.put("P_BENEFICIARIO", nz(contrato.getNombreBeneficiario()));
        params.put("P_CAT", pct(cat));
        params.put("P_TASA_ANUAL", pct(tasaAnual));
        params.put("P_MONTO_PRESTAMO", money(contrato.getMontoPrestamo()));
        params.put("P_MONTO_TOTAL_PAGAR", money(montoTotalPagar));
        params.put("P_NUMERO_CONTRATO", nz(contrato.getFolio()));
        params.put("P_FECHA_FIRMA", contrato.getFechaApertura() != null
                ? contrato.getFechaApertura().format(DF) : "");
        params.put("P_MONTO_AVALUO", money(contrato.getMontoAvaluo()));
        params.put("P_PORCENTAJE_PRESTAMO_AVALUO", pct(porcPrestSobreAvaluo));
        params.put("P_FECHA_INICIO_COMERCIALIZACION",
                contrato.getFechaVencimiento() != null ? contrato.getFechaVencimiento().format(DF) : "");
        params.put("P_FECHA_LIMITE_FINIQUITO",
                contrato.getFechaVencimiento() != null ? contrato.getFechaVencimiento().format(DF) : "");
        params.put("P_DOMICILIO_SUCURSAL", domicilioSucursal(sucursal));
        params.put("P_TELEFONO_SUCURSAL", sucursal != null ? nz(sucursal.getTelefono()) : "");
        params.put("P_PAGINA_INTERNET", "www.prestamil.com.mx");
        params.put("P_NOMBRE_SUCURSAL", sucursal != null ? nz(sucursal.getNombre()) : "");
        params.put("P_HORARIO_ATENCION", sucursal != null ? nz(sucursal.getHorarioAtencion()) : "");
        params.put("P_IVA", MONEY.format(IVA_PORCENTAJE) + " %");
        params.put("P_CLIENTE_DIRECCION", direccionCliente(contrato.getCliente()));
        params.put("P_CREDENCIAL_LECTOR", nz(contrato.getNumIdentificacion()));
        params.put("P_GMS", MONEY.format(pesoTotal));
        params.put("P_RMO", ramo(primera));
        params.put("P_REFRENDO", money(refrendoFinal));
        params.put("P_RESUMEN_INTERES", plain(porcInteres));
        params.put("P_RESUMEN_CUSTODIA", plain(porcAlmacen));
        params.put("P_RESUMEN_GTO_OPER", plain(comisionVenta));
        params.put("P_RESUMEN_MORATORIOS", plain(porcSancion));
        params.put("P_PLAZO_PRESTAMO", plazoTexto(plazo));
        params.put("P_AVALUO_TOTALES", "Monto del avalúo: " + money(contrato.getMontoAvaluo())
                + "   |   % préstamo sobre avalúo: " + pct(porcPrestSobreAvaluo));
        params.put("P_PRENDAS", new JRBeanCollectionDataSource(buildPrendas(partidas)));
        params.put("P_PAGOS", new JRBeanCollectionDataSource(buildPagos(vencimientos, contrato)));
        // ⏳ Sanción por extemporaneidad pendiente de verificar vs COCAE (Fase 7): se usa la fórmula
        // documentada (2% por semana vencida sobre el préstamo, sumada al interés y con IVA).
        List<PagoExtemporaneoRow> extemporaneos = buildPagosExtemporaneos(
                contrato, porcInteres, porcAlmacen, porcGastos, porcSancion, plazo, ultimo.getFecha());
        params.put("P_MOSTRAR_PAGO_EXTEMPORANEO", !extemporaneos.isEmpty());
        params.put("P_PAGOS_EXTEMPORANEOS", new JRBeanCollectionDataSource(extemporaneos));

        try {
            JasperPrint print = JasperFillManager.fillReport(getReporte(), params, new JREmptyDataSource());
            return JasperExportManager.exportReportToPdf(print);
        } catch (JRException e) {
            throw new BadRequestException("No se pudo generar el PDF del contrato: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------------
    // Construcción de filas de las tablas
    // ---------------------------------------------------------------------

    private List<PrendaRow> buildPrendas(List<PartidaContrato> partidas) {
        List<PrendaRow> filas = new ArrayList<>();
        for (PartidaContrato p : partidas) {
            String caracteristicas = (p.getKilataje() != null ? p.getKilataje() + "K " : "")
                    + (p.getHechura() != null ? p.getHechura() + " " : "")
                    + (p.getLey() != null ? "Ley " + plain(p.getLey()) + " " : "")
                    + (p.getPesoGramos() != null ? MONEY.format(p.getPesoGramos()) + " g" : "");
            BigDecimal porc = valOr(p.getAvaluoContrato()).compareTo(BigDecimal.ZERO) > 0
                    ? valOr(p.getMontoPrestamo()).multiply(new BigDecimal(100))
                        .divide(p.getAvaluoContrato(), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            filas.add(new PrendaRow(
                    nz(p.getDescripcion()),
                    caracteristicas.trim(),
                    money(p.getAvaluoContrato()),
                    money(p.getMontoPrestamo()),
                    pct(porc)));
        }
        return filas;
    }

    private List<PagoRow> buildPagos(List<VencimientoResponse> vencimientos, Contrato contrato) {
        List<PagoRow> filas = new ArrayList<>();
        String mutuo = money(contrato.getMontoPrestamo());
        for (VencimientoResponse v : vencimientos) {
            BigDecimal refrendo = valOr(v.getTotalInteres()).add(valOr(v.getIva()));
            filas.add(new PagoRow(
                    String.valueOf(v.getPeriodo()),
                    mutuo,
                    money(v.getInteres()),
                    money(v.getAlmacen()),
                    money(v.getIva()),
                    money(refrendo),
                    money(v.getDesempeno()),
                    cuandoPago(v),
                    "NORMAL"));
        }
        return filas;
    }

    // ---------------------------------------------------------------------
    // Helpers de formato
    // ---------------------------------------------------------------------

    private synchronized JasperReport getReporte() {
        if (reporte == null) {
            // Compilamos desde el .jrxml en runtime (una vez, cacheado) para usar siempre la
            // plantilla fuente vigente y no un .jasper potencialmente desactualizado.
            try (InputStream is = new ClassPathResource("jasper/contrato.jrxml").getInputStream()) {
                reporte = JasperCompileManager.compileReport(is);
            } catch (IOException | JRException e) {
                throw new BadRequestException("No se pudo cargar la plantilla del contrato: " + e.getMessage());
            }
        }
        return reporte;
    }

    /** Fecha estilo COCAE: dd/mmm/yyyy (mes abreviado en español). Ej. 18/ago/2026. */
    private String fechaAbrev(java.time.LocalDate d) {
        if (d == null) return "";
        return String.format("%02d/%s/%d", d.getDayOfMonth(), MESES_ABR[d.getMonthValue() - 1], d.getYear());
    }

    /** Texto de la columna "cuándo se realizan los pagos", estilo COCAE: "S 1-hasta el ->18/ago/2026". */
    private String cuandoPago(VencimientoResponse v) {
        if (v.getFecha() == null) return "";
        return "S " + v.getPeriodo() + "-hasta el ->" + fechaAbrev(v.getFecha());
    }

    /**
     * Filas del bloque "Pago Extemporáneo": los ~2 periodos siguientes al plazo, con la sanción por
     * extemporaneidad. Fórmula documentada (por Jorge, ⏳ pendiente de verificar vs COCAE — Fase 7):
     * {@code sancion = prestamo × porcSancionSemanal/100 × semanasVencidas}; la sanción se suma al
     * interés y sobre el total se aplica el IVA (16%, truncado como COCAE). El préstamo sigue
     * acumulando interés/almacenaje en cada periodo extra.
     */
    private List<PagoExtemporaneoRow> buildPagosExtemporaneos(
            Contrato contrato, BigDecimal porcInteres, BigDecimal porcAlmacen, BigDecimal porcGastos,
            BigDecimal porcSancion, Plazo plazo, java.time.LocalDate baseFecha) {
        List<PagoExtemporaneoRow> filas = new ArrayList<>();
        BigDecimal prestamo = valOr(contrato.getMontoPrestamo());
        if (prestamo.compareTo(BigDecimal.ZERO) <= 0) return filas;
        int n = Math.max(plazo.getNumeroPeriodos(), 0);
        int dias = Math.max(plazo.getDiasPorPeriodo(), 1);
        BigDecimal cien = new BigDecimal("100");
        String mutuo = money(prestamo);
        final int semanasExtra = 2; // COCAE imprime ~2 periodos extra por espacio de la hoja
        for (int k = 1; k <= semanasExtra; k++) {
            int j = n + k; // periodo acumulado (plazo + k periodos vencidos)
            BigDecimal periodoAcum = new BigDecimal(j);
            BigDecimal interes  = prestamo.multiply(porcInteres).multiply(periodoAcum).divide(cien, 2, RoundingMode.HALF_UP);
            BigDecimal almacen  = prestamo.multiply(porcAlmacen).multiply(periodoAcum).divide(cien, 2, RoundingMode.HALF_UP);
            BigDecimal gastos   = prestamo.multiply(porcGastos).multiply(periodoAcum).divide(cien, 2, RoundingMode.HALF_UP);
            BigDecimal sancion  = prestamo.multiply(porcSancion).multiply(new BigDecimal(k)).divide(cien, 2, RoundingMode.HALF_UP);
            // El interés impreso lleva embebidos gastos admin y sanción (COCAE no les da columna aparte).
            BigDecimal interesConSancion = interes.add(gastos).add(sancion);
            BigDecimal totalInteres = interesConSancion.add(almacen);
            BigDecimal iva = totalInteres.multiply(IVA_PORCENTAJE).divide(cien, 2, RoundingMode.DOWN);
            BigDecimal refrendo = totalInteres.add(iva);
            BigDecimal desempeno = prestamo.add(refrendo);
            String cuando = baseFecha != null
                    ? "S " + j + "-hasta el ->" + fechaAbrev(baseFecha.plusDays((long) dias * k))
                    : "";
            filas.add(new PagoExtemporaneoRow(
                    "S " + j,                    // concepto
                    mutuo,                       // importeMutuo
                    money(interesConSancion),    // intereses (incluye gastos admin + sanción)
                    money(almacen),              // almacenaje
                    money(iva),                  // iva
                    money(desempeno),            // porDesempeno
                    cuando,                      // cuandoSeRealizaPago
                    money(refrendo)));           // porRefrendo
        }
        return filas;
    }

    private BigDecimal valOr(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    private String nz(String s) { return s != null ? s : ""; }

    private String money(BigDecimal v) { return "$" + MONEY.format(valOr(v)); }

    private String pct(BigDecimal v) { return MONEY.format(valOr(v)) + " %"; }

    private String plain(BigDecimal v) {
        return v != null ? v.stripTrailingZeros().toPlainString() : "0";
    }

    private String nombreCliente(Cliente c) {
        if (c == null) return "";
        return (nz(c.getNombre()) + " " + nz(c.getApellidoPaterno()) + " " + nz(c.getApellidoMaterno())).trim();
    }

    private String direccionCliente(Cliente c) {
        if (c == null || c.getDireccion() == null) return "";
        Direccion d = c.getDireccion();
        return (nz(d.getCalle()) + " " + nz(d.getNumeroExterior()) + ", " + nz(d.getColonia())
                + ", " + nz(d.getCiudad()) + ", " + nz(d.getEstado()) + " C.P. " + nz(d.getCodigoPostal()))
                .replaceAll("\\s+,", ",").trim();
    }

    private String domicilioSucursal(Sucursal s) {
        if (s == null) return "";
        return (nz(s.getCalle()) + " " + nz(s.getNoExterior()) + ", " + nz(s.getColonia())
                + ", " + nz(s.getMunicipio()) + ", " + nz(s.getEstado()) + " C.P. " + nz(s.getCp()))
                .replaceAll("\\s+,", ",").trim();
    }

    private String ramo(PartidaContrato p) {
        String tipo = p.getTipoPrenda() != null && p.getTipoPrenda().getTipo() != null
                ? p.getTipoPrenda().getTipo().toUpperCase() : "";
        if (tipo.startsWith("ALHAJA")) return "AL";
        if (tipo.startsWith("PLATA")) return "PL";
        if (tipo.startsWith("VARIOS")) return "VA";
        return "";
    }

    private String plazoTexto(Plazo plazo) {
        String periodicidad;
        switch (plazo.getDiasPorPeriodo()) {
            case 1:  periodicidad = "DÍA(S)"; break;
            case 7:  periodicidad = "SEMANA(S)"; break;
            case 15: periodicidad = "QUINCENA(S)"; break;
            case 30: periodicidad = "MES(ES)"; break;
            default: periodicidad = "PERIODO(S)"; break;
        }
        return plazo.getNumeroPeriodos() + " " + periodicidad + " CALENDARIO";
    }
}
