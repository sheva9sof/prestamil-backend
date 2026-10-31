package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Cierre de un día de caja (CAJ-06): el corte congelado tal como estaba al cerrar. {@code corteJson} guarda
 * el {@code CorteCajaResultado} completo, que es lo que se muestra de ese día desde entonces; las columnas
 * de importes repiten sus cifras clave para consultarlas sin leer el JSON. El saldo final es el saldo
 * anterior del siguiente día (RC-08).
 */
@Entity
@Table(name = "cierre_caja_diario", uniqueConstraints = @UniqueConstraint(
        name = "uq_cierre_caja_diario_sucursal_fecha", columnNames = {"id_sucursal", "fecha"}))
@Getter
@Setter
public class CierreCajaDiario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "id_sucursal", nullable = false)
    private Integer sucursalId;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "saldo_anterior", nullable = false, precision = 18, scale = 2)
    private BigDecimal saldoAnterior;

    @Column(name = "ministraciones", nullable = false, precision = 18, scale = 2)
    private BigDecimal ministraciones;

    @Column(name = "concentraciones", nullable = false, precision = 18, scale = 2)
    private BigDecimal concentraciones;

    @Column(name = "boveda", nullable = false, precision = 18, scale = 2)
    private BigDecimal boveda;

    @Column(name = "dotaciones", nullable = false, precision = 18, scale = 2)
    private BigDecimal dotaciones;

    @Column(name = "ingresos_efectivo", nullable = false, precision = 18, scale = 2)
    private BigDecimal ingresosEfectivo;

    @Column(name = "egresos_efectivo", nullable = false, precision = 18, scale = 2)
    private BigDecimal egresosEfectivo;

    @Column(name = "recolecciones", nullable = false, precision = 18, scale = 2)
    private BigDecimal recolecciones;

    @Column(name = "diferencia", nullable = false, precision = 18, scale = 2)
    private BigDecimal diferencia;

    @Column(name = "saldo_final", nullable = false, precision = 18, scale = 2)
    private BigDecimal saldoFinal;

    @Column(name = "corte_json", nullable = false, columnDefinition = "LONGTEXT")
    private String corteJson;

    /** {@code true} si lo cerró Sistemas después de su día (B-04). */
    @Column(name = "dia_pendiente", nullable = false)
    private Boolean diaPendiente = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "fecha_cierre", nullable = false)
    private LocalDateTime fechaCierre;
}
