package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "movimiento_contrato")
@Getter
@Setter
public class MovimientoContrato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_contrato", nullable = false)
    private Contrato contrato;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_turno", nullable = false)
    private Turno turno;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoMovimiento tipo;

    /** Total cobrado en el movimiento (interés + sanción + IVA + abono + capital). En EMP, el préstamo entregado. */
    @Column(name = "monto", nullable = false, precision = 18, scale = 2)
    private BigDecimal monto;

    @Column(name = "interes", precision = 18, scale = 2)
    private BigDecimal interes;

    @Column(name = "sancion", nullable = false, precision = 18, scale = 2, columnDefinition = "DECIMAL(18,2) DEFAULT 0.00")
    private BigDecimal sancion = BigDecimal.ZERO;

    @Column(name = "abono_capital", nullable = false, precision = 18, scale = 2, columnDefinition = "DECIMAL(18,2) DEFAULT 0.00")
    private BigDecimal abonoCapital = BigDecimal.ZERO;

    /** Periodos extemporáneos cubiertos (semanas de atraso cobradas con sanción). */
    @Column(name = "semanas_vencidas", nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer semanasVencidas = 0;

    @Column(name = "periodos_normales")
    private Integer periodosNormales;

    /** Días de gracia consumidos (columna "Dias G" de COCAE). */
    @Column(name = "dias_gracia_usados", nullable = false)
    private Integer diasGraciaUsados = 0;

    /** Interés + almacenaje de un periodo, sin redondear (RN-10). */
    @Column(name = "interes_por_periodo", precision = 18, scale = 4)
    private BigDecimal interesPorPeriodo;

    /** Descuento sobre intereses parametrizado, copiado al momento de la operación (RN-27). */
    @Column(name = "porc_descuento_interes", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcDescuentoInteres = BigDecimal.ZERO;

    @Column(name = "importe_descuento", nullable = false, precision = 18, scale = 2)
    private BigDecimal importeDescuento = BigDecimal.ZERO;

    @Column(name = "iva", nullable = false, precision = 18, scale = 2)
    private BigDecimal iva = BigDecimal.ZERO;

    // Forma de pago (ventana "Cobro", RN-24). De la tarjeta solo se guardan los últimos 4 dígitos (PCI DSS).

    /** Efectivo recibido; el que entra a caja es importeEfectivo - cambioEntregado. */
    @Column(name = "importe_efectivo", precision = 18, scale = 2)
    private BigDecimal importeEfectivo;

    @Column(name = "importe_tarjeta", precision = 18, scale = 2)
    private BigDecimal importeTarjeta;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_tarjeta", length = 10)
    private TipoTarjeta tipoTarjeta;

    @Column(name = "tarjeta_ultimos4", length = 4, columnDefinition = "CHAR(4)")
    private String tarjetaUltimos4;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_banco_emisor")
    private Banco bancoEmisor;

    @Column(name = "autorizacion_banco", length = 30)
    private String autorizacionBanco;

    @Column(name = "cambio_entregado", precision = 18, scale = 2)
    private BigDecimal cambioEntregado;

    // Estado del contrato antes y después del movimiento: la cancelación (F10) restaura los *Anterior.

    @Column(name = "saldo_anterior", precision = 18, scale = 2)
    private BigDecimal saldoAnterior;

    @Column(name = "saldo_nuevo", precision = 18, scale = 2)
    private BigDecimal saldoNuevo;

    @Column(name = "fecha_contrato_anterior")
    private LocalDate fechaContratoAnterior;

    @Column(name = "fecha_venc_anterior")
    private LocalDate fechaVencAnterior;

    @Column(name = "fecha_contrato_nueva")
    private LocalDate fechaContratoNueva;

    @Column(name = "fecha_venc_nueva")
    private LocalDate fechaVencNueva;

    @Enumerated(EnumType.STRING)
    @Column(name = "estatus_anterior", length = 20)
    private EstatusContrato estatusAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estatus_nuevo", length = 20)
    private EstatusContrato estatusNuevo;

    @Column(name = "num_refrendos_anterior")
    private Integer numRefrendosAnterior;

    // Cancelación (RN-26): el movimiento nunca se borra, queda marcado.

    @Column(name = "cancelado", nullable = false)
    private Boolean cancelado = false;

    @Column(name = "fecha_cancelacion")
    private LocalDateTime fechaCancelacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_cancela")
    private Usuario usuarioCancela;

    @Column(name = "motivo_cancelacion", length = 300)
    private String motivoCancelacion;

    /** Idempotencia: un requestId repetido devuelve el movimiento ya creado en vez de cobrar dos veces. */
    @Column(name = "request_id", length = 36, unique = true)
    private String requestId;

    /** Folio consecutivo de la nota por sucursal (RN-25). Null en EMP y en movimientos anteriores a F3. */
    @Column(name = "folio_nota")
    private Integer folioNota;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @Column(name = "observaciones", length = 300)
    private String observaciones;
}
