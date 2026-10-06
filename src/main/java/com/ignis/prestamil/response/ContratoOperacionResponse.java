package com.ignis.prestamil.response;

import com.ignis.prestamil.model.AccionContrato;
import com.ignis.prestamil.model.EstatusOperativo;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * Fila del listado de la pantalla de Finiquitos y Refrendos (GET /api/contratos/operacion). El
 * estatus y las acciones se derivan a la fecha del servidor (RN-16); el frontend no los recalcula.
 */
@Getter
@Setter
public class ContratoOperacionResponse {
    private Long id;
    private String folio;

    private String nombrePlazo;
    private Integer numeroPeriodos;
    private Integer diasPorPeriodo;
    /** Tipo de prenda de la primera partida. */
    private String ramo;
    private Integer numPartidas;

    private EstatusOperativo estatus;
    private Integer numRefrendos;

    private Integer idCliente;
    private String nombreCliente;

    /** Inicio del periodo vigente (RN-02). */
    private LocalDate fechaContrato;
    private LocalDate fechaVencimiento;
    /** Días de gracia sin sanción efectivos del contrato (snapshot > vigente). */
    private Integer diasGracia;
    private LocalDate fechaComercializacion;

    private BigDecimal montoPrestamo;
    private BigDecimal saldoCapital;
    private BigDecimal montoAvaluo;
    /** Interés + almacenaje de un periodo sobre el saldo, sin IVA ("Int x Per." de COCAE). */
    private BigDecimal interesPorPeriodo;

    private Set<AccionContrato> accionesDisponibles;

    /**
     * Motivo por el que las acciones de cobro quedaron fuera de {@code accionesDisponibles}; hoy solo
     * lo pobla la regla "un movimiento por contrato por día" (RN-29, C-01). Null si no hubo bloqueo.
     * El frontend lo usa como tooltip del botón deshabilitado.
     */
    private String motivoAccionesDeshabilitadas;
}
