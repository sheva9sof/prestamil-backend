package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Precio base del gramo de oro de 24 quilates vigente por sucursal.
 * Al actualizarse, dispara el recálculo de todas las tablas de hechura/kilataje.
 */
@Entity
@Table(name = "precio_oro")
@Getter
@Setter
public class PrecioOro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "sucursal_id", nullable = false, unique = true)
    private Integer sucursalId;

    @Column(name = "precio_gramo_24k", nullable = false, precision = 12, scale = 4)
    private BigDecimal precioGramo24k = BigDecimal.ZERO;

    @Column(name = "calcular_sobre", nullable = false, length = 20)
    private String calcularSobre = "PRESTAMO";

    @Column(name = "base_kilataje", nullable = false)
    private Integer baseKilataje = 24;

    /** Factor de hechura "Fundir" (calidad baja). 90 = 90% del precio base. */
    @Column(name = "factor_fundir", nullable = false, precision = 7, scale = 4)
    private BigDecimal factorFundir = new BigDecimal("90.0000");

    /** Factor de hechura "Normal". 100 = 100% del precio base. */
    @Column(name = "factor_normal", nullable = false, precision = 7, scale = 4)
    private BigDecimal factorNormal = new BigDecimal("100.0000");

    /** Factor de hechura "Especial" (mayor calidad). 110 = 110% del precio base. */
    @Column(name = "factor_especial", nullable = false, precision = 7, scale = 4)
    private BigDecimal factorEspecial = new BigDecimal("110.0000");

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    @Column(name = "actualizado_por", length = 80)
    private String actualizadoPor;
}
