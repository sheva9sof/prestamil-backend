package com.ignis.prestamil.service;

import com.ignis.prestamil.model.ConceptoCaja;
import com.ignis.prestamil.model.MovimientoContrato;
import com.ignis.prestamil.model.OrigenCaja;
import com.ignis.prestamil.model.Turno;
import com.ignis.prestamil.model.Usuario;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Datos de un flujo de caja para {@link MovimientoCajaService#registrar(RegistroCaja)} (CAJ-01b, A-03).
 * La dirección (ENTRADA/SALIDA) y el renglón del corte salen del {@link ConceptoCaja}; el importe va
 * separado por forma de pago porque solo el efectivo entra al arqueo (RC-06).
 *
 * <p><b>Ejemplo para Ventas (A-03)</b>: venta de vitrina de $1,500 pagada con $1,000 en tarjeta y un
 * billete de $1,000 en efectivo (cambio de $500). El efectivo va <b>neto del cambio</b>:</p>
 * <pre>{@code
 * movimientoCajaService.registrar(new RegistroCaja(
 *         turnoActivo,                       // TurnoService: el turno abierto de la sucursal
 *         venta.getSucursalId(),
 *         ConceptoCaja.VENTA_VITRINA,        // VENTA_FUNDICION o CANCELACION_VENTA según el caso
 *         new BigDecimal("500.00"),          // efectivo recibido 1000.00 - cambio 500.00
 *         new BigDecimal("1000.00"),         // tarjeta
 *         OrigenCaja.VENTA,                  // CANCELACION_VENTA para una cancelación
 *         venta.getId(),                     // sin FK (A-02)
 *         usuario,
 *         "VENTA NOTA " + venta.getFolio()));  // texto libre, se recorta a 120 caracteres
 * }</pre>
 * <p>Se llama dentro de la transacción de la venta, después de guardarla (para tener su id). Nunca se
 * escribe directo en {@code movimiento_caja}.</p>
 *
 * @param turno           turno en el que ocurre el flujo
 * @param sucursalId      sucursal del flujo
 * @param concepto        renglón del corte y dirección del flujo
 * @param importeEfectivo efectivo que entra o sale de la gaveta, neto del cambio; {@code >= 0}
 * @param importeTarjeta  parte con tarjeta; {@code >= 0}
 * @param origenTipo      tabla de origen del flujo; null solo para flujos administrativos
 * @param origenId        id del registro de origen; obligatorio si hay {@code origenTipo}
 * @param usuario         usuario que registra el flujo
 * @param descripcion     texto para auditoría y tickets (columna {@code concepto})
 */
public record RegistroCaja(Turno turno,
                           Integer sucursalId,
                           ConceptoCaja concepto,
                           BigDecimal importeEfectivo,
                           BigDecimal importeTarjeta,
                           OrigenCaja origenTipo,
                           Long origenId,
                           Usuario usuario,
                           String descripcion) {

    public RegistroCaja {
        Objects.requireNonNull(turno, "turno");
        Objects.requireNonNull(sucursalId, "sucursalId");
        Objects.requireNonNull(concepto, "concepto");
        Objects.requireNonNull(importeEfectivo, "importeEfectivo");
        Objects.requireNonNull(importeTarjeta, "importeTarjeta");
        Objects.requireNonNull(usuario, "usuario");
        if (importeEfectivo.signum() < 0 || importeTarjeta.signum() < 0) {
            throw new IllegalArgumentException("Los importes de caja no pueden ser negativos: efectivo="
                    + importeEfectivo + " tarjeta=" + importeTarjeta);
        }
        if (importeEfectivo.add(importeTarjeta).signum() == 0) {
            throw new IllegalArgumentException("Un flujo de caja de $0 no se registra (" + concepto + ")");
        }
        if (descripcion == null || descripcion.isBlank()) {
            throw new IllegalArgumentException("La descripción del flujo de caja es obligatoria");
        }
    }

    /** Total del flujo: efectivo más tarjeta. Se guarda en {@code movimiento_caja.monto}. */
    public BigDecimal total() {
        return importeEfectivo.add(importeTarjeta);
    }

    /**
     * Flujo originado por un movimiento de contrato. El importe se reparte como se pagó el movimiento:
     * efectivo neto = {@code importeEfectivo - cambioEntregado} y tarjeta = {@code importeTarjeta}. Un
     * movimiento que no pasó por la ventana de Cobro (EMP) va completo en efectivo. La cancelación de un
     * cobro usa el mismo reparto que el cobro original.
     *
     * @param mov         movimiento de contrato ya guardado
     * @param concepto    concepto de caja ({@link ConceptoCaja#deMovimiento} o {@link ConceptoCaja#deCancelacion})
     * @param turno       turno en el que ocurre el flujo
     * @param sucursalId  sucursal del contrato
     * @param usuario     usuario que registra el flujo
     * @param descripcion texto para auditoría
     * @return el registro listo para {@link MovimientoCajaService#registrar(RegistroCaja)}
     */
    public static RegistroCaja deMovimientoContrato(MovimientoContrato mov, ConceptoCaja concepto, Turno turno,
                                                   Integer sucursalId, Usuario usuario, String descripcion) {
        BigDecimal efectivo;
        BigDecimal tarjeta;
        if (mov.getImporteEfectivo() == null && mov.getImporteTarjeta() == null) {
            efectivo = mov.getMonto();
            tarjeta = BigDecimal.ZERO;
        } else {
            efectivo = cero(mov.getImporteEfectivo()).subtract(cero(mov.getCambioEntregado()));
            tarjeta = cero(mov.getImporteTarjeta());
        }
        return new RegistroCaja(turno, sucursalId, concepto, efectivo, tarjeta,
                OrigenCaja.MOVIMIENTO_CONTRATO, mov.getId(), usuario, descripcion);
    }

    private static BigDecimal cero(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }
}
