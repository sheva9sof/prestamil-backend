package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.exception.ForbiddenException;
import com.ignis.prestamil.model.Configuracion;
import com.ignis.prestamil.model.Rol;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.ConfiguracionRepository;
import com.ignis.prestamil.repository.SucursalRepository;
import com.ignis.prestamil.repository.UsuarioRepository;
import com.ignis.prestamil.request.ReporteFiltro;
import com.ignis.prestamil.response.CorteCajaMensualContextoResponse;
import com.ignis.prestamil.service.calculo.CorteCajaResultado;
import com.ignis.prestamil.service.calculo.CorteCajaService;
import com.ignis.prestamil.service.calculo.SaldoArranque;
import com.ignis.prestamil.service.calculo.SaldoArranqueProvider;
import com.ignis.prestamil.util.Constantes;
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
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reporte mensual de corte de caja en PDF y Excel (REP-06, ROD-1.0), con el formato del "REPORTE MENSUAL
 * DE CORTE DE CAJA" de COCAE sobre la base de REP-00 (encabezado estándar, {@link ReporteFiltro},
 * {@link ReporteJasper}). Un renglón por día con el bloque inferior del corte: saldo inicial,
 * ministraciones, concentraciones, bóveda, dotaciones, ingresos, egresos, recolección, diferencia F/S y
 * saldo final, más una fila de totales.
 *
 * <p>Una sola fuente (RC-09): cada día es el {@link CorteCajaResultado} de
 * {@code CorteCajaService.calcularRango}, que ya entrega congelados los días cerrados (su
 * {@code cierre_caja_diario}) y calcula los abiertos arrastrando el saldo. Aquí no se suma nada salvo la
 * fila de totales; la diferencia total es la suma de las diferencias diarias.</p>
 *
 * <p>Días especiales:</p>
 * <ul>
 *   <li>Los anteriores al arranque no aparecen; si todo el mes es anterior, sale el encabezado con el aviso.</li>
 *   <li>Los pendientes de cierre (ya pasaron, tuvieron movimientos y nadie los cerró) se marcan "PENDIENTE".</li>
 *   <li>El mes en curso llega hasta hoy.</li>
 *   <li>Los días sin movimientos sí aparecen, con el saldo arrastrado (COCAE los omite).</li>
 * </ul>
 *
 * <p>Lo sacan los roles de {@code ROLES_PERMITIDOS_CORTE_CAJA_MENSUAL} (gerente y Sistemas); el cajero
 * recibe 403. Igual que REP-04, es de toda la gaveta: un ramo o un rango que no sea un mes responden 400.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CorteCajaMensualReporteService {

    static final String PLANTILLA = "jasper/corte-caja-mensual.jrxml";
    static final String TITULO = "REPORTE MENSUAL DE CORTE DE CAJA";
    static final String CODIGO_RPT = "ROD-1.0";
    static final String PENDIENTE = "PENDIENTE";

    /** Tipos de fila de la plantilla: cada uno es una banda del detalle. */
    static final String DIA = "DIA";
    static final String TOTAL = "TOTAL";
    static final String NOTA = "NOTA";

    private static final Locale MX = Locale.forLanguageTag("es-MX");
    private static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(2);

    private final CorteCajaService corteCajaService;
    private final CierreCajaService cierreCajaService;
    private final SaldoArranqueProvider saldoArranqueProvider;
    private final UsuarioRepository usuarioRepository;
    private final ConfiguracionRepository configuracionRepository;
    private final SucursalRepository sucursalRepository;
    private final Clock clock;

    /**
     * Un día del mensual: el bloque inferior de su corte.
     *
     * @param fecha        día
     * @param saldoInicial saldo de la bóveda al abrir el día (el saldo final del anterior, RC-08)
     * @param diferencia   arqueo de ventanilla (RC-07)
     * @param indicador    F, S o {@code null} si la diferencia es 0
     * @param pendiente    el día ya pasó, tuvo movimientos y no se ha cerrado
     */
    public record Dia(LocalDate fecha, BigDecimal saldoInicial, BigDecimal ministraciones,
                      BigDecimal concentraciones, BigDecimal boveda, BigDecimal dotaciones, BigDecimal ingresos,
                      BigDecimal egresos, BigDecimal recolecciones, BigDecimal diferencia,
                      CorteCajaResultado.Indicador indicador, BigDecimal saldoFinal, boolean pendiente) {
    }

    /**
     * Fila de totales: la suma de cada columna de flujos. Los saldos no se suman (como en COCAE).
     *
     * @param diferencia suma de las diferencias diarias
     * @param indicador  FALTANTE, SOBRANTE o {@code null} si la suma es 0
     */
    public record Totales(BigDecimal ministraciones, BigDecimal concentraciones, BigDecimal dotaciones,
                          BigDecimal ingresos, BigDecimal egresos, BigDecimal recolecciones,
                          BigDecimal diferencia, String indicador) {
    }

    /**
     * El mensual armado, antes de pasar a la plantilla.
     *
     * @param mes     mes del reporte
     * @param hasta   último día con renglón: el fin de mes o, en el mes en curso, hoy
     * @param aviso   por qué no hay cifras (todo el mes antes del arranque, o sin arranque); {@code null} si hay
     * @param dias    un renglón por día, desde el primero del mes (o el de arranque) hasta {@code hasta}
     * @param totales fila de totales; {@code null} si no hay cifras
     * @param notas   aclaraciones al pie (pendientes, mes en curso, arranque a medio mes, F/S)
     */
    public record CorteCajaMensual(YearMonth mes, LocalDate hasta, String aviso, List<Dia> dias, Totales totales,
                                   List<String> notas) {

        /** Si el mes tiene cifras. */
        public boolean valido() {
            return aviso == null;
        }
    }

    /**
     * Reporte generado.
     *
     * @param mes   mes del reporte
     * @param bytes contenido del archivo
     */
    public record Archivo(YearMonth mes, byte[] bytes) {
    }

    /**
     * Arranque de la pantalla: hoy del servidor y la fecha de arranque de caja, para el selector de mes.
     *
     * @param sucursalId sucursal
     * @param username   usuario que abre la pantalla
     * @return hoy y la fecha de arranque ({@code null} si no hay)
     * @throws ForbiddenException si el rol no puede sacar el mensual
     */
    public CorteCajaMensualContextoResponse contexto(Integer sucursalId, String username) {
        validarPermiso(username);
        LocalDate fechaArranque = saldoArranqueProvider.saldoArranque(sucursalId).map(SaldoArranque::fecha).orElse(null);
        return new CorteCajaMensualContextoResponse(LocalDate.now(clock), fechaArranque);
    }

    /**
     * Mensual en PDF.
     *
     * @param sucursalId sucursal
     * @param filtro     {@code desde} = primer día del mes y {@code hasta} = último (o sin él); sin ramo.
     *                   Sin {@code desde}, el mes en curso
     * @param username   usuario que lo pide
     * @return el mes y los bytes del PDF
     * @throws ForbiddenException  si el rol no puede sacar el mensual
     * @throws BadRequestException si llega un ramo, un rango que no es un mes o un mes futuro
     */
    public Archivo pdf(Integer sucursalId, ReporteFiltro filtro, String username) {
        CorteCajaMensual mensual = mensual(sucursalId, filtro, username);
        return new Archivo(mensual.mes(), ReporteJasper.pdf(ReporteJasper.plantilla(PLANTILLA),
                parametros(sucursalId, mensual), datos(mensual)));
    }

    /**
     * Mensual en Excel (.xlsx), de la misma plantilla que el PDF y con los importes como números.
     *
     * @param sucursalId sucursal
     * @param filtro     mismos filtros que {@link #pdf}
     * @param username   usuario que lo pide
     * @return el mes y los bytes del .xlsx
     * @throws ForbiddenException  si el rol no puede sacar el mensual
     * @throws BadRequestException si llega un ramo, un rango que no es un mes o un mes futuro
     */
    public Archivo xlsx(Integer sucursalId, ReporteFiltro filtro, String username) {
        CorteCajaMensual mensual = mensual(sucursalId, filtro, username);
        return new Archivo(mensual.mes(), ReporteJasper.xlsx(ReporteJasper.plantilla(PLANTILLA),
                parametros(sucursalId, mensual), datos(mensual)));
    }

    /**
     * Arma el mensual: valida permiso y filtro, pide los cortes del mes a {@code CorteCajaService} y suma
     * la fila de totales.
     *
     * @param sucursalId sucursal
     * @param filtro     mes pedido como rango (ver {@link #pdf})
     * @param username   usuario que lo pide
     * @return los renglones del mes, sus totales y sus notas
     * @throws ForbiddenException  si el rol no puede sacar el mensual
     * @throws BadRequestException si llega un ramo, un rango que no es un mes o un mes futuro
     */
    public CorteCajaMensual mensual(Integer sucursalId, ReporteFiltro filtro, String username) {
        // 1. Permiso y mes pedido
        validarPermiso(username);
        LocalDate hoy = LocalDate.now(clock);
        YearMonth mes = mes(filtro, hoy);
        if (mes.isAfter(YearMonth.from(hoy))) {
            throw new BadRequestException("No hay corte de caja de " + nombreMes(mes) + ": es un mes futuro.");
        }

        // 2. El mes en curso llega hasta hoy
        LocalDate desde = mes.atDay(1);
        LocalDate hasta = mes.equals(YearMonth.from(hoy)) ? hoy : mes.atEndOfMonth();

        // 3. Una sola fuente: los cerrados llegan congelados, los abiertos calculados con el saldo arrastrado
        List<CorteCajaResultado> cortes = corteCajaService.calcularRango(sucursalId, desde, hasta);
        List<CorteCajaResultado> conCifras = cortes.stream().filter(CorteCajaResultado::valido).toList();
        if (conCifras.isEmpty()) {
            // Todo el mes es anterior al arranque, o no hay arranque: los días traen el mismo aviso
            return new CorteCajaMensual(mes, hasta, cortes.get(cortes.size() - 1).aviso(), List.of(), null, List.of());
        }

        // 4. Renglones, con los días pendientes de cierre marcados
        Set<LocalDate> pendientes = cierreCajaService.diasPendientes(sucursalId, desde, hasta);
        List<Dia> dias = conCifras.stream()
                .map(c -> new Dia(c.fecha(), c.saldoAnterior(), c.ministraciones(), c.concentraciones(),
                        c.boveda(), c.dotaciones(), c.ingresosEfectivo(), c.egresosEfectivo(), c.recolecciones(),
                        c.diferencia(), c.indicador(), c.saldoFinal(), pendientes.contains(c.fecha())))
                .toList();

        // 5. Lo único que se suma aquí: la fila de totales
        BigDecimal diferencia = suma(dias, Dia::diferencia);
        Totales totales = new Totales(suma(dias, Dia::ministraciones), suma(dias, Dia::concentraciones),
                suma(dias, Dia::dotaciones), suma(dias, Dia::ingresos), suma(dias, Dia::egresos),
                suma(dias, Dia::recolecciones), diferencia,
                switch (diferencia.signum()) {
                    case 1 -> "FALTANTE";
                    case -1 -> "SOBRANTE";
                    default -> null;
                });

        return new CorteCajaMensual(mes, hasta, null, dias, totales, notas(dias, desde, hasta, hoy));
    }

    /** Aclaraciones al pie, solo las que aplican al mes. */
    private static List<String> notas(List<Dia> dias, LocalDate desde, LocalDate hasta, LocalDate hoy) {
        List<String> notas = new ArrayList<>();
        LocalDate primerDia = dias.get(0).fecha();
        if (primerDia.isAfter(desde)) {
            notas.add("El corte de caja empieza el " + primerDia.format(DD_MM_YYYY)
                    + " (saldo de arranque): los días anteriores no aparecen.");
        }
        if (hasta.equals(hoy)) {
            notas.add("Mes en curso: llega hasta hoy, " + hoy.format(DD_MM_YYYY)
                    + ", con lo registrado hasta ahora.");
        }
        if (dias.stream().anyMatch(Dia::pendiente)) {
            notas.add("PENDIENTE: el día tuvo movimientos y no se ha cerrado; sus cifras pueden cambiar "
                    + "hasta que Sistemas lo cierre.");
        }
        notas.add("Diferencia: lo que debe estar en la gaveta. F = faltante, S = sobrante.");
        return notas;
    }

    /**
     * Mes del filtro. El mensual es de toda la gaveta y de un mes completo: sin ramo, {@code desde} el día 1
     * y {@code hasta} el último día de ese mes (o sin él).
     */
    private static YearMonth mes(ReporteFiltro filtro, LocalDate hoy) {
        if (filtro.ramo() != null) {
            throw new BadRequestException("El corte de caja es de toda la sucursal: no se filtra por ramo.");
        }
        if (filtro.desde() == null) {
            if (filtro.hasta() != null) {
                throw new BadRequestException("Manda el mes del reporte: desde el día 1 hasta el último día del mes.");
            }
            return YearMonth.from(hoy);
        }
        YearMonth mes = YearMonth.from(filtro.desde());
        boolean completo = filtro.desde().getDayOfMonth() == 1
                && (filtro.hasta() == null || filtro.hasta().equals(mes.atEndOfMonth()));
        if (!completo) {
            throw new BadRequestException("El reporte mensual de corte de caja es de un mes completo: del "
                    + mes.atDay(1).format(DD_MM_YYYY) + " al " + mes.atEndOfMonth().format(DD_MM_YYYY) + ".");
        }
        return mes;
    }

    private static BigDecimal suma(List<Dia> dias, Function<Dia, BigDecimal> columna) {
        return dias.stream().map(columna).reduce(CERO, BigDecimal::add);
    }

    /** Encabezado estándar y aviso. Package-private para los tests. */
    Map<String, Object> parametros(Integer sucursalId, CorteCajaMensual mensual) {
        Sucursal sucursal = sucursalRepository.findById(sucursalId).orElse(null);
        // Como COCAE: el encabezado dice el mes completo aunque el mes en curso llegue hasta hoy
        String fechas = new ReporteFiltro(null, mensual.mes().atDay(1), mensual.mes().atEndOfMonth()).textoFechas();
        Map<String, Object> params = EncabezadoReporte.estandar(sucursal, TITULO, fechas,
                LocalDateTime.now(clock), CODIGO_RPT).parametros();
        // Separador de miles y punto decimal de México en el PDF
        params.put(JRParameter.REPORT_LOCALE, MX);
        params.put("P_MES", nombreMes(mensual.mes()));
        if (!mensual.valido()) {
            params.put("P_AVISO_TITULO", "Sin corte de caja de " + nombreMes(mensual.mes()));
            params.put("P_AVISO", mensual.aviso());
        }
        return params;
    }

    /** Filas de la plantilla: los días, los totales y las notas. Package-private para los tests. */
    JRMapCollectionDataSource datos(CorteCajaMensual mensual) {
        return new JRMapCollectionDataSource(mensual.valido() ? filas(mensual) : List.of());
    }

    static List<Map<String, ?>> filas(CorteCajaMensual mensual) {
        List<Map<String, ?>> filas = new ArrayList<>();
        for (Dia d : mensual.dias()) {
            Map<String, Object> fila = fila(DIA, d.fecha().format(DD_MM_YYYY));
            fila.put("saldoInicial", d.saldoInicial());
            fila.put("ministraciones", d.ministraciones());
            fila.put("concentraciones", d.concentraciones());
            fila.put("boveda", d.boveda());
            fila.put("dotaciones", d.dotaciones());
            fila.put("ingresos", d.ingresos());
            fila.put("egresos", d.egresos());
            fila.put("recolecciones", d.recolecciones());
            fila.put("diferencia", d.diferencia());
            fila.put("marca", d.indicador() != null ? d.indicador().name() : null);
            fila.put("saldoFinal", d.saldoFinal());
            fila.put("estado", d.pendiente() ? PENDIENTE : null);
            filas.add(fila);
        }

        Totales t = mensual.totales();
        Map<String, Object> total = fila(TOTAL, "Totales");
        total.put("ministraciones", t.ministraciones());
        total.put("concentraciones", t.concentraciones());
        total.put("dotaciones", t.dotaciones());
        total.put("ingresos", t.ingresos());
        total.put("egresos", t.egresos());
        total.put("recolecciones", t.recolecciones());
        total.put("diferencia", t.diferencia());
        total.put("marca", t.indicador());
        filas.add(total);

        for (String nota : mensual.notas()) {
            filas.add(fila(NOTA, nota));
        }
        return filas;
    }

    private static Map<String, Object> fila(String tipo, String concepto) {
        Map<String, Object> fila = new HashMap<>();
        fila.put("tipo", tipo);
        fila.put("concepto", concepto);
        return fila;
    }

    /** "septiembre de 2026". */
    private static String nombreMes(YearMonth mes) {
        return mes.getMonth().getDisplayName(TextStyle.FULL, MX) + " de " + mes.getYear();
    }

    private void validarPermiso(String username) {
        Integer rolId = usuarioRepository.findByNombreUsuario(username)
                .map(Usuario::getRol)
                .map(Rol::getId)
                .orElse(null);
        if (rolId == null || !rolesPermitidos().contains(rolId)) {
            throw new ForbiddenException("El reporte mensual de corte de caja solo lo sacan el gerente y Sistemas.");
        }
    }

    /** CSV de ids de rol de la configuración; conjunto vacío = nadie lo saca. */
    private Set<Integer> rolesPermitidos() {
        return configuracionRepository.findByConfiguracion(Constantes.ROLES_PERMITIDOS_CORTE_CAJA_MENSUAL)
                .map(Configuracion::getValorCadena)
                .filter(csv -> csv != null && !csv.isBlank())
                .map(csv -> Arrays.stream(csv.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(Integer::parseInt)
                        .collect(Collectors.toSet()))
                .orElseGet(Collections::emptySet);
    }
}
