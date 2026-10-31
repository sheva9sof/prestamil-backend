package com.ignis.prestamil.service.calculo;

import com.ignis.prestamil.model.RenglonCorte;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Corte de caja de un día (CAJ-05), con los dos bloques del PDF "CORTE DE CAJA" de COCAE. Lo calcula
 * {@link CorteCajaService}, la fuente única de las cifras de caja (RC-09): la pantalla, REP-04, REP-05 y
 * REP-06 lo leen y no suman por su cuenta.
 *
 * <p>Si el día es anterior al arranque de caja, o todavía no hay saldo de arranque, el corte no tiene
 * cifras: {@code aviso} dice por qué, los renglones vienen vacíos y los importes en {@code null}.</p>
 *
 * @param sucursalId      sucursal del corte
 * @param fecha           día del corte
 * @param aviso           por qué el corte no tiene cifras; {@code null} si es válido
 * @param renglones       bloque superior: los renglones de {@link RenglonCorte} en su orden, con efectivo y
 *                        tarjeta
 * @param ingresos        total de la columna de ingresos, DOTACIONES incluidas
 * @param egresos         total de la columna de egresos, RECOLECCIÓN incluida
 * @param saldoAnterior   saldo de la bóveda al abrir el día: el saldo final del día anterior (RC-08)
 * @param ministraciones  traspasos de tesorería a la bóveda
 * @param concentraciones traspasos de la bóveda al banco
 * @param boveda          {@code saldoAnterior + ministraciones − concentraciones} (RC-08)
 * @param dotaciones      traspasos de la bóveda a la ventanilla
 * @param ingresosEfectivo ingresos de ventanilla en efectivo, sin DOTACIONES (RC-05, RC-06)
 * @param egresosEfectivo egresos de ventanilla en efectivo, sin RECOLECCIÓN (RC-05)
 * @param recolecciones   traspasos de la ventanilla a la bóveda
 * @param diferencia      arqueo de ventanilla: {@code dotaciones + ingresosEfectivo − egresosEfectivo −
 *                        recolecciones} (RC-07); es lo que debería quedar en la gaveta
 * @param indicador       {@link Indicador#F} si la diferencia es mayor que 0, {@link Indicador#S} si es
 *                        menor y {@code null} si es 0
 * @param saldoFinal      {@code boveda − dotaciones + recolecciones} (RC-08); saldo anterior del día siguiente
 */
public record CorteCajaResultado(
        Integer sucursalId,
        LocalDate fecha,
        String aviso,
        List<Renglon> renglones,
        Totales ingresos,
        Totales egresos,
        BigDecimal saldoAnterior,
        BigDecimal ministraciones,
        BigDecimal concentraciones,
        BigDecimal boveda,
        BigDecimal dotaciones,
        BigDecimal ingresosEfectivo,
        BigDecimal egresosEfectivo,
        BigDecimal recolecciones,
        BigDecimal diferencia,
        Indicador indicador,
        BigDecimal saldoFinal) {

    /** Faltante o sobrante del arqueo de ventanilla (RC-07). */
    public enum Indicador { F, S }

    /**
     * Renglón del bloque superior. Solo el efectivo entra al arqueo; la tarjeta es informativa (RC-06).
     * Puede ser negativo el día en que se cancela una operación de otro día (Q-C1).
     */
    public record Renglon(RenglonCorte renglon, String etiqueta, RenglonCorte.Columna columna,
                          BigDecimal efectivo, BigDecimal tarjeta) {

        /** Efectivo más tarjeta. */
        public BigDecimal total() {
            return efectivo.add(tarjeta);
        }
    }

    /** Suma de una columna del bloque superior, separada por forma de pago. */
    public record Totales(BigDecimal efectivo, BigDecimal tarjeta) {

        /** Efectivo más tarjeta. */
        public BigDecimal total() {
            return efectivo.add(tarjeta);
        }
    }

    /**
     * Corte sin cifras: antes del arranque de caja o sin saldo de arranque.
     *
     * @param sucursalId sucursal del corte
     * @param fecha      día del corte
     * @param aviso      por qué no hay cifras
     * @return corte con el aviso, sin renglones y con los importes en {@code null}
     */
    public static CorteCajaResultado sinCifras(Integer sucursalId, LocalDate fecha, String aviso) {
        return new CorteCajaResultado(sucursalId, fecha, aviso, List.of(), null, null,
                null, null, null, null, null, null, null, null, null, null, null);
    }

    /** Si el corte tiene cifras. */
    public boolean valido() {
        return aviso == null;
    }

    /**
     * Renglón del bloque superior.
     *
     * @param renglon renglón buscado
     * @return el renglón con su efectivo y tarjeta
     * @throws IllegalStateException si el corte no tiene cifras
     */
    public Renglon renglon(RenglonCorte renglon) {
        return renglones.stream()
                .filter(r -> r.renglon() == renglon)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("El corte del " + fecha + " no tiene cifras: " + aviso));
    }
}
