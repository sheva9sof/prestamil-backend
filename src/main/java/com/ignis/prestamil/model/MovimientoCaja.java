package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Movimiento de efectivo en caja (C-07). Base del corte de caja: cada entrada o salida de dinero
 * queda asociada al turno, la sucursal y, cuando aplica, al movimiento de contrato que la originó.
 *
 * <p>Primera incorporación (C-06): la cancelación de un empeño registra una ENTRADA por el
 * préstamo que el cliente devuelve. Los cobros, salidas por devolución de pago con tarjeta y demás
 * se integrarán en C-07.</p>
 */
@Entity
@Table(name = "movimiento_caja")
@Getter
@Setter
public class MovimientoCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_turno", nullable = false)
    private Turno turno;

    @Column(name = "id_sucursal", nullable = false)
    private Integer sucursalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 10)
    private TipoMovimientoCaja tipo;

    @Column(name = "concepto", nullable = false, length = 120)
    private String concepto;

    @Column(name = "monto", nullable = false, precision = 18, scale = 2)
    private BigDecimal monto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    /** Movimiento de contrato que originó esta entrada/salida; null para movimientos administrativos. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_movimiento_contrato")
    private MovimientoContrato movimientoContrato;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;
}
