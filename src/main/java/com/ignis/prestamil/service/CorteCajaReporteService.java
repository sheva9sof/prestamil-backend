package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.model.RenglonCorte;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.repository.SucursalRepository;
import com.ignis.prestamil.request.ReporteFiltro;
import com.ignis.prestamil.response.CorteCajaResponse;
import com.ignis.prestamil.service.calculo.CorteCajaResultado;
import com.ignis.prestamil.util.EncabezadoReporte;
import com.ignis.prestamil.util.ReporteJasper;
import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Corte de caja diario en PDF y Excel (REP-04, RPT.DR-1.1.0), con el formato del "CORTE DE CAJA" de COCAE
 * sobre la base de REP-00 (encabezado estándar, {@link ReporteFiltro}, {@link ReporteJasper}).
 *
 * <p>Las cifras salen solo de {@code CorteCajaService.calcular} (RC-09), vía {@link CorteCajaConsultaService},
 * que además decide el día: el cajero siempre saca el de hoy y gerente y Sistemas eligen (como la pantalla
 * CAJ-07a). Aquí no se suma nada: cada importe del reporte es un campo de {@link CorteCajaResultado}, a lo
 * más con el signo con el que mueve la bóveda o la ventanilla.</p>
 *
 * <p>Tres bloques, en este orden:</p>
 * <ol>
 *   <li>Movimientos de ventanilla en efectivo: los renglones de {@link RenglonCorte} en el orden de COCAE,
 *       con REPOSICIONES y sin renglones de cancelaciones (restan en el de su operación, Q-C1).</li>
 *   <li>Cobrado con tarjeta, informativo: no entra a la gaveta ni al arqueo (RC-06).</li>
 *   <li>Caja: saldo anterior, ministraciones, concentraciones, bóveda, dotaciones, ingresos, egresos,
 *       recolecciones, diferencia F/S y saldo final (RC-07, RC-08).</li>
 * </ol>
 * <p>Antes del arranque o sin saldo de arranque el reporte sale con el encabezado y el aviso, sin cifras.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CorteCajaReporteService {

    static final String PLANTILLA = "jasper/corte-caja.jrxml";
    static final String TITULO = "CORTE DE CAJA";
    static final String CODIGO_RPT = "RPT.DR-1.1.0";

    /** Tipos de fila de la plantilla: cada uno es una banda del detalle. */
    static final String SECCION = "SECCION";
    static final String RENGLON = "RENGLON";
    static final String TOTAL = "TOTAL";
    static final String NOTA = "NOTA";

    private static final Locale MX = Locale.forLanguageTag("es-MX");
    private static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CorteCajaConsultaService corteCajaConsultaService;
    private final SucursalRepository sucursalRepository;
    private final Clock clock;

    /**
     * Corte de caja de un día en PDF.
     *
     * @param sucursalId sucursal
     * @param filtro     {@code desde} = día del corte (sin él, hoy); sin rango ni ramo
     * @param username   usuario que lo pide; si es cajero, el corte es el de hoy
     * @return el día del corte y los bytes del PDF
     * @throws BadRequestException si se pide un rango, un ramo o un día futuro
     */
    public Archivo pdf(Integer sucursalId, ReporteFiltro filtro, String username) {
        CorteCajaResponse consulta = consultar(sucursalId, filtro, username);
        return new Archivo(consulta.fecha(), ReporteJasper.pdf(ReporteJasper.plantilla(PLANTILLA),
                parametros(sucursalId, consulta.corte()), datos(consulta.corte())));
    }

    /**
     * Corte de caja de un día en Excel (.xlsx), de la misma plantilla que el PDF y con los importes como
     * números.
     *
     * @param sucursalId sucursal
     * @param filtro     {@code desde} = día del corte (sin él, hoy); sin rango ni ramo
     * @param username   usuario que lo pide; si es cajero, el corte es el de hoy
     * @return el día del corte y los bytes del .xlsx
     * @throws BadRequestException si se pide un rango, un ramo o un día futuro
     */
    public Archivo xlsx(Integer sucursalId, ReporteFiltro filtro, String username) {
        CorteCajaResponse consulta = consultar(sucursalId, filtro, username);
        return new Archivo(consulta.fecha(), ReporteJasper.xlsx(ReporteJasper.plantilla(PLANTILLA),
                parametros(sucursalId, consulta.corte()), datos(consulta.corte())));
    }

    /**
     * Reporte generado y el día que realmente se reportó (para el cajero, hoy aunque haya pedido otro).
     *
     * @param fecha día del corte
     * @param bytes contenido del archivo
     */
    public record Archivo(LocalDate fecha, byte[] bytes) {
    }

    /** Valida el filtro y pide el corte con las mismas reglas de fecha que la pantalla (CAJ-07a). */
    private CorteCajaResponse consultar(Integer sucursalId, ReporteFiltro filtro, String username) {
        // El corte es de un solo día (Q-08) y de toda la gaveta: el efectivo no se separa por ramo
        if (filtro.ramo() != null) {
            throw new BadRequestException("El corte de caja es de toda la sucursal: no se filtra por ramo.");
        }
        if (filtro.hasta() != null && !filtro.hasta().equals(filtro.desde())) {
            throw new BadRequestException("El corte de caja es de un solo día: manda solo la fecha del corte.");
        }
        return corteCajaConsultaService.consultar(sucursalId, filtro.desde(), username);
    }

    /** Encabezado estándar, totales y aviso. Package-private para los tests. */
    Map<String, Object> parametros(Integer sucursalId, CorteCajaResultado corte) {
        Sucursal sucursal = sucursalRepository.findById(sucursalId).orElse(null);
        String fechas = new ReporteFiltro(null, corte.fecha(), corte.fecha()).textoFechas();
        Map<String, Object> params = EncabezadoReporte.estandar(sucursal, TITULO, fechas,
                LocalDateTime.now(clock), CODIGO_RPT).parametros();
        // Separador de miles y punto decimal de México en el PDF
        params.put(JRParameter.REPORT_LOCALE, MX);
        params.put("P_FECHA_CORTE", corte.fecha().format(DD_MM_YYYY));
        if (!corte.valido()) {
            params.put("P_AVISO_TITULO", "Sin corte de caja para el " + corte.fecha().format(DD_MM_YYYY));
            params.put("P_AVISO", corte.aviso());
        }
        return params;
    }

    /** Filas de la plantilla: los tres bloques ya armados. Package-private para los tests. */
    JRMapCollectionDataSource datos(CorteCajaResultado corte) {
        return new JRMapCollectionDataSource(corte.valido() ? filas(corte) : List.of());
    }

    static List<Map<String, ?>> filas(CorteCajaResultado corte) {
        List<Map<String, ?>> filas = new ArrayList<>();

        // 1. Movimientos de ventanilla en efectivo, en el orden de COCAE
        filas.add(fila(SECCION, "MOVIMIENTOS (EFECTIVO)", null, null, null, "INGRESOS", "EGRESOS"));
        for (CorteCajaResultado.Renglon r : corte.renglones()) {
            filas.add(renglon(r.etiqueta(), r.columna(), r.efectivo()));
        }
        filas.add(fila(TOTAL, "TOTALES", corte.ingresos().efectivo(), corte.egresos().efectivo(), null, null, null));

        // 2. Tarjeta: solo los renglones que tuvieron cobros con tarjeta (RC-06)
        filas.add(fila(SECCION, "COBRADO CON TARJETA (INFORMATIVO)", null, null, null, "INGRESOS", "EGRESOS"));
        List<CorteCajaResultado.Renglon> conTarjeta = corte.renglones().stream()
                .filter(r -> r.tarjeta().signum() != 0)
                .toList();
        if (conTarjeta.isEmpty()) {
            filas.add(nota("Sin cobros con tarjeta en el día."));
        } else {
            for (CorteCajaResultado.Renglon r : conTarjeta) {
                filas.add(renglon(r.etiqueta(), r.columna(), r.tarjeta()));
            }
            filas.add(fila(TOTAL, "TOTAL TARJETA", corte.ingresos().tarjeta(), corte.egresos().tarjeta(),
                    null, null, null));
        }
        filas.add(nota("No entra a la gaveta ni al arqueo."));

        // 3. Caja: cada importe con el signo con el que mueve la bóveda o la ventanilla (RC-07, RC-08)
        filas.add(fila(SECCION, "CAJA", null, null, null, "BÓVEDA", "VENTANILLA"));
        filas.add(fila(RENGLON, "SALDO ANTERIOR", corte.saldoAnterior(), null, null, null, null));
        filas.add(fila(RENGLON, "MINISTRACIONES", corte.ministraciones(), null, null, null, null));
        filas.add(fila(RENGLON, "CONCENTRACIONES", corte.concentraciones().negate(), null, null, null, null));
        filas.add(fila(TOTAL, "BÓVEDA", corte.boveda(), null, null, null, null));
        filas.add(fila(RENGLON, "DOTACIONES", corte.dotaciones().negate(), corte.dotaciones(), null, null, null));
        filas.add(fila(RENGLON, "INGRESOS", null, corte.ingresosEfectivo(), null, null, null));
        filas.add(fila(RENGLON, "EGRESOS", null, corte.egresosEfectivo().negate(), null, null, null));
        filas.add(fila(RENGLON, "RECOLECCIONES", corte.recolecciones(), corte.recolecciones().negate(),
                null, null, null));
        filas.add(fila(TOTAL, "DIFERENCIA", null, corte.diferencia(),
                corte.indicador() != null ? corte.indicador().name() : null, null, null));
        filas.add(fila(TOTAL, "SALDO FINAL", corte.saldoFinal(), null, null, null, null));
        filas.add(nota("Diferencia: lo que debe estar en la gaveta. F = faltante, S = sobrante."));
        return filas;
    }

    /** Renglón del corte con su importe en la columna de ingresos o de egresos. */
    private static Map<String, ?> renglon(String etiqueta, RenglonCorte.Columna columna, BigDecimal importe) {
        boolean ingreso = columna == RenglonCorte.Columna.INGRESO;
        return fila(RENGLON, etiqueta, ingreso ? importe : null, ingreso ? null : importe, null, null, null);
    }

    private static Map<String, ?> nota(String texto) {
        return fila(NOTA, texto, null, null, null, null, null);
    }

    /**
     * Fila de la plantilla. Las dos columnas de importes son números para que el Excel los tome como
     * tales; {@code titulo1}/{@code titulo2} son los encabezados de esas columnas en las filas de sección.
     */
    private static Map<String, ?> fila(String tipo, String concepto, BigDecimal importe1, BigDecimal importe2,
                                       String marca, String titulo1, String titulo2) {
        Map<String, Object> fila = new HashMap<>();
        fila.put("tipo", tipo);
        fila.put("concepto", concepto);
        fila.put("importe1", importe1);
        fila.put("importe2", importe2);
        fila.put("marca", marca);
        fila.put("titulo1", titulo1);
        fila.put("titulo2", titulo2);
        return fila;
    }
}
