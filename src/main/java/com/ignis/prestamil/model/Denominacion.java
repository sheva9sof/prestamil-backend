package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Billete o moneda del desglose de traspasos y del arqueo (RC-03). Catálogo global; la semilla son las
 * 14 denominaciones de COCAE, de 1000 a 0.01 (Q-10).
 */
@Entity
@Table(name = "denominacion")
@Getter
@Setter
public class Denominacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "valor", nullable = false, unique = true, precision = 10, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 10)
    private TipoDenominacion tipo;

    /** Orden de captura e impresión, de mayor a menor valor. */
    @Column(name = "orden", nullable = false)
    private Integer orden;

    @Column(name = "activa", nullable = false)
    private Boolean activa = true;
}
