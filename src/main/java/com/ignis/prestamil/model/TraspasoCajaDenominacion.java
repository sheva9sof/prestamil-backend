package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Renglón del desglose de un {@link TraspasoCaja}: cuántas piezas de una denominación se movieron (RC-03).
 */
@Entity
@Table(name = "traspaso_caja_denominacion", uniqueConstraints = @UniqueConstraint(
        name = "uq_traspaso_caja_denominacion", columnNames = {"id_traspaso", "id_denominacion"}))
@Getter
@Setter
public class TraspasoCajaDenominacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_traspaso", nullable = false)
    private TraspasoCaja traspaso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_denominacion", nullable = false)
    private Denominacion denominacion;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    /** {@code cantidad × denominacion.valor}. */
    @Column(name = "importe", nullable = false, precision = 18, scale = 2)
    private BigDecimal importe;
}
