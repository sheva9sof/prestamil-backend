package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Flujo de dinero en caja (C-07, CAJ-01b). Base del corte de caja: cada entrada o salida queda
 * asociada al turno, la sucursal, su {@link ConceptoCaja} (renglón del corte) y el registro que la
 * originó. El importe va separado por forma de pago porque solo el efectivo entra al arqueo (RC-06).
 * Se escribe únicamente con {@code MovimientoCajaService.registrar(RegistroCaja)}.
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

    /** Descripción libre para auditoría y tickets (ej. "COBRO REFRENDO FOLIO 12 CONTRATO CTR-000123"). */
    @Column(name = "concepto", nullable = false, length = 120)
    private String concepto;

    /** Renglón del corte al que pertenece el flujo (H-02). */
    @Enumerated(EnumType.STRING)
    @Column(name = "concepto_caja", nullable = false, length = 40)
    private ConceptoCaja conceptoCaja;

    /** Total del flujo: {@code importeEfectivo + importeTarjeta}. */
    @Column(name = "monto", nullable = false, precision = 18, scale = 2)
    private BigDecimal monto;

    /** Efectivo que entra o sale de la gaveta, neto del cambio (H-01). */
    @Column(name = "importe_efectivo", nullable = false, precision = 18, scale = 2)
    private BigDecimal importeEfectivo;

    /** Parte con tarjeta; informativa en el corte, no entra al arqueo (H-01). */
    @Column(name = "importe_tarjeta", nullable = false, precision = 18, scale = 2)
    private BigDecimal importeTarjeta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    /** Movimiento de contrato que originó esta entrada/salida; null para movimientos administrativos. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_movimiento_contrato")
    private MovimientoContrato movimientoContrato;

    /** Tabla de origen del flujo; junto con {@link #origenId} identifica el registro sin FK (A-02). */
    @Enumerated(EnumType.STRING)
    @Column(name = "origen_tipo", length = 30)
    private OrigenCaja origenTipo;

    @Column(name = "origen_id")
    private Long origenId;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;
}
