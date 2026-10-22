package com.ignis.prestamil.response;

import com.ignis.prestamil.model.EstatusContrato;
import com.ignis.prestamil.model.TipoMovimiento;
import com.ignis.prestamil.model.TipoTarjeta;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class MovimientoResponse {
    private Long id;
    /** Folio de la nota (ticket); null en EMP y en movimientos anteriores a F3. */
    private Integer folioNota;
    private Long idContrato;
    private String folioContrato;
    private TipoMovimiento tipo;
    private BigDecimal monto;
    private BigDecimal interes;
    private BigDecimal sancion;
    private BigDecimal abonoCapital;
    private Integer semanasVencidas;
    private Integer periodosNormales;
    private Integer diasGraciaUsados;
    private BigDecimal interesPorPeriodo;
    private BigDecimal porcDescuentoInteres;
    private BigDecimal importeDescuento;
    private BigDecimal iva;
    private LocalDateTime fecha;
    private String observaciones;
    private String nombreUsuario;
    private Integer numRefrendos;
    private LocalDateTime nuevaFechaVencimiento;

    // Forma de pago
    private BigDecimal importeEfectivo;
    private BigDecimal importeTarjeta;
    private TipoTarjeta tipoTarjeta;
    private String tarjetaUltimos4;
    private String bancoEmisor;
    private String autorizacionBanco;
    private BigDecimal cambioEntregado;

    // Estado del contrato antes y después
    private BigDecimal saldoAnterior;
    private BigDecimal saldoNuevo;
    private LocalDate fechaContratoAnterior;
    private LocalDate fechaVencAnterior;
    private LocalDate fechaContratoNueva;
    private LocalDate fechaVencNueva;
    private EstatusContrato estatusAnterior;
    private EstatusContrato estatusNuevo;

    // Cancelación
    private Boolean cancelado;
    private LocalDateTime fechaCancelacion;
    private String usuarioCancela;
    private String motivoCancelacion;
}
