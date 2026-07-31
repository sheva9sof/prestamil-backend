package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
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

    @Column(name = "monto", nullable = false, precision = 18, scale = 2)
    private BigDecimal monto;

    @Column(name = "interes", precision = 18, scale = 2)
    private BigDecimal interes;

    @Column(name = "sancion", nullable = false, precision = 18, scale = 2, columnDefinition = "DECIMAL(18,2) DEFAULT 0.00")
    private BigDecimal sancion = BigDecimal.ZERO;

    @Column(name = "abono_capital", nullable = false, precision = 18, scale = 2, columnDefinition = "DECIMAL(18,2) DEFAULT 0.00")
    private BigDecimal abonoCapital = BigDecimal.ZERO;

    @Column(name = "semanas_vencidas", nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer semanasVencidas = 0;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @Column(name = "observaciones", length = 300)
    private String observaciones;
}
