package com.ignis.prestamil.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "plazo_hechura_alhaja")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlazoHechuraAlhaja {

    @EmbeddedId
    private PlazoHechuraAlhajaId id;

    @Column(name = "tabla_prestamo_id", nullable = false)
    private Integer tablaPrestamoId;

    @Column(name = "precio_base", nullable = false, precision = 12, scale = 4)
    private BigDecimal precioBase;

    @Column(name = "porc_aumento", nullable = false, precision = 7, scale = 4)
    private BigDecimal porcAumento;

    @Column(name = "precio_prestamo", nullable = false, precision = 12, scale = 4)
    private BigDecimal precioPrestamo;
}
