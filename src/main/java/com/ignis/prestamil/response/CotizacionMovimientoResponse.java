package com.ignis.prestamil.response;

import com.ignis.prestamil.model.AccionContrato;
import com.ignis.prestamil.model.EstatusOperativo;
import com.ignis.prestamil.model.TipoMovimiento;
import com.ignis.prestamil.model.TipoOperacion;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Cotización de una operación sobre un contrato (POST /api/movimientos/cotizacion). Solo lectura:
 * al registrar, el servidor vuelve a calcular todo (RN-19).
 */
@Getter
@Setter
public class CotizacionMovimientoResponse {
    private Long contratoId;
    private String folio;
    private TipoOperacion tipoOperacion;
    private TipoMovimiento tipoMovimiento;

    // Estado actual
    private EstatusOperativo estatusActual;
    private Set<AccionContrato> accionesDisponibles;
    /**
     * Motivo por el que una acción de cobro fue removida de {@code accionesDisponibles}; hoy solo lo
     * pobla la regla "un movimiento por contrato por día" (RN-29, C-01). Null si no hubo bloqueo.
     */
    private String motivoAccionesDeshabilitadas;
    private LocalDate fechaContrato;
    private LocalDate fechaVencimiento;
    private BigDecimal saldoCapital;

    // Periodos
    private Integer diasAtraso;
    private Integer diasGraciaUsados;
    private Integer periodosTranscurridos;
    private Integer periodosNormales;
    private Integer periodosExtemporaneos;
    private Integer periodosMaximos;
    private Integer periodosAplicados;
    private Integer periodosNormalesAplicados;
    private Integer periodosExtemporaneosAplicados;
    private Integer semanasSancion;

    // Montos
    private BigDecimal interesPorPeriodo;
    private BigDecimal interes;
    private BigDecimal almacen;
    private BigDecimal interesTotal;
    private BigDecimal porcSancionSemanal;
    private BigDecimal sancion;
    private BigDecimal descuento;
    private BigDecimal subtotal;
    private BigDecimal porcIva;
    private BigDecimal iva;
    private BigDecimal abonoCapital;
    private BigDecimal capital;
    private BigDecimal total;
    /** "NOVENTA Y CINCO PESOS 92/100 M.N.": la ventana de Cobro y el ticket usan el mismo texto. */
    private String totalConLetra;

    // Cómo queda el contrato
    private BigDecimal saldoNuevo;
    private LocalDate fechaContratoNueva;
    private LocalDate fechaVencimientoNueva;
    private LocalDate fechaComercializacionNueva;
    private EstatusOperativo estatusNuevo;

    private List<String> advertencias;
}
