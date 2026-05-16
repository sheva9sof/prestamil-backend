package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "plazo_parametro")
@Getter
@Setter
@IdClass(PlazoParametroId.class)
public class PlazoParametro {

    @Id
    @Column(name = "plazo_id", nullable = false)
    private Long plazoId;

    @Id
    @Column(name = "tipo_prenda_id", nullable = false)
    private Integer tipoPrendaId;

    @Id
    @Column(name = "sucursal_id", nullable = false)
    private Integer sucursalId;

    @Column(name = "porc_interes", nullable = false, precision = 9, scale = 4, columnDefinition = "DECIMAL(9,4) DEFAULT 0.0000")
    private BigDecimal porcInteres = BigDecimal.ZERO;

    @Column(name = "porc_almacen", nullable = false, precision = 9, scale = 4, columnDefinition = "DECIMAL(9,4) DEFAULT 0.0000")
    private BigDecimal porcAlmacen = BigDecimal.ZERO;

    @Column(name = "porc_gastos_admin", nullable = false, precision = 9, scale = 4, columnDefinition = "DECIMAL(9,4) DEFAULT 0.0000")
    private BigDecimal porcGastosAdmin = BigDecimal.ZERO;

    @Column(name = "porc_interes_total", nullable = false, precision = 9, scale = 4, columnDefinition = "DECIMAL(9,4) DEFAULT 0.0000")
    private BigDecimal porcInteresTotal = BigDecimal.ZERO;

    @Column(name = "cat", nullable = false, precision = 9, scale = 4, columnDefinition = "DECIMAL(9,4) DEFAULT 0.0000")
    private BigDecimal cat = BigDecimal.ZERO;

    @Column(name = "num_max_refrendos", nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer numMaxRefrendos = 0;

    @Column(name = "porc_prestamo_s_avaluo", nullable = false, precision = 9, scale = 4, columnDefinition = "DECIMAL(9,4) DEFAULT 0.0000")
    private BigDecimal porcPrestamoSAvaluo = BigDecimal.ZERO;

    @Column(name = "usa_avaluo_real", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean usaAvaluoReal = false;

    @Column(name = "porc_prestamo_s_avaluo_real", nullable = false, precision = 9, scale = 4, columnDefinition = "DECIMAL(9,4) DEFAULT 0.0000")
    private BigDecimal porcPrestamoSAvaluoReal = BigDecimal.ZERO;

    @Column(name = "cobrar_reposicion_contrato", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean cobrarReposicionContrato = false;

    @Column(name = "reposicion_es_porcentaje", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean reposicionEsPorcentaje = false;

    @Column(name = "porc_reposicion", nullable = false, precision = 9, scale = 4, columnDefinition = "DECIMAL(9,4) DEFAULT 0.0000")
    private BigDecimal porcReposicion = BigDecimal.ZERO;

    @Column(name = "monto_reposicion", nullable = false, precision = 18, scale = 2, columnDefinition = "DECIMAL(18,2) DEFAULT 0.00")
    private BigDecimal montoReposicion = BigDecimal.ZERO;

    @Column(name = "comision_por_venta_prenda", nullable = false, precision = 9, scale = 4, columnDefinition = "DECIMAL(9,4) DEFAULT 0.0000")
    private BigDecimal comisionPorVentaPrenda = BigDecimal.ZERO;

    @Column(name = "aplicar_sancion_por_periodo", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean aplicarSancionPorPeriodo = false;

    @Column(name = "dias_gracia_sin_interes", nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer diasGraciaSinInteres = 0;

    @Column(name = "dias_antes_pase_venta", nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer diasAntesPaseVenta = 0;

    @Column(name = "importe_min_prestamo", nullable = false, precision = 18, scale = 2, columnDefinition = "DECIMAL(18,2) DEFAULT 0.00")
    private BigDecimal importeMinPrestamo = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plazo_id", insertable = false, updatable = false)
    private Plazo plazo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_prenda_id", insertable = false, updatable = false)
    private TipoPrenda tipoPrenda;

}

