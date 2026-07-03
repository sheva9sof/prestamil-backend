package com.ignis.prestamil.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Tabla global de %Prestamo COCAE (8 kilates x 3 hechuras) por sucursal.
 * Fuente de verdad de solo lectura para derivar precio_base en PlazoHechuraAlhaja
 * (ver PlazoService.recalcularRegistros). No tiene endpoint ni pantalla de edicion
 * en este phase (D-02/D-03) — se puebla unicamente via changeset Liquibase.
 */
@Entity
@Table(name = "oro_tabla_prestamo")
@Getter
@Setter
public class OroTablaPrestamo {

    @EmbeddedId
    private OroTablaPrestamoId id;

    @Column(name = "porc_prestamo", nullable = false, precision = 7, scale = 4)
    private BigDecimal porcPrestamo;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;
}
