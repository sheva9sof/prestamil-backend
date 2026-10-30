package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Traspaso de dinero entre dos cajas de la sucursal (RC-02): ministración, concentración, dotación o
 * recolección. Lleva su desglose por denominación (RC-03) y nunca se borra; la cancelación lo marca.
 */
@Entity
@Table(name = "traspaso_caja", uniqueConstraints = @UniqueConstraint(
        name = "uq_traspaso_caja_sucursal_folio", columnNames = {"id_sucursal", "folio"}))
@Getter
@Setter
public class TraspasoCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "id_sucursal", nullable = false)
    private Integer sucursalId;

    /** Consecutivo por sucursal de {@code FolioService}, serie {@link SerieFolio#TRASPASO_CAJA}. */
    @Column(name = "folio", nullable = false)
    private Long folio;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoTraspasoCaja tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_caja_origen", nullable = false)
    private Caja cajaOrigen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_caja_destino", nullable = false)
    private Caja cajaDestino;

    /** Suma del desglose (RC-03). */
    @Column(name = "importe", nullable = false, precision = 18, scale = 2)
    private BigDecimal importe;

    /** Día del corte al que pertenece el traspaso; lo pone el servidor. */
    @Column(name = "fecha_operacion", nullable = false)
    private LocalDate fechaOperacion;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    /** Turno activo al registrar; null si se registró sin turno abierto (ministración, concentración). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_turno")
    private Turno turno;

    /** Ficha bancaria de la concentración (Q-07), opcional. */
    @Column(name = "referencia_bancaria", length = 60)
    private String referenciaBancaria;

    @Enumerated(EnumType.STRING)
    @Column(name = "estatus", nullable = false, length = 10)
    private EstatusTraspasoCaja estatus = EstatusTraspasoCaja.ACTIVO;

    @Column(name = "fecha_cancelacion")
    private LocalDateTime fechaCancelacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_cancela")
    private Usuario usuarioCancela;

    @Column(name = "motivo_cancelacion", length = 300)
    private String motivoCancelacion;

    @OneToMany(mappedBy = "traspaso", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TraspasoCajaDenominacion> desglose = new ArrayList<>();
}
