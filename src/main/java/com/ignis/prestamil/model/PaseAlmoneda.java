package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Bitacora del pase de almoneda diario (F11, RN-04 / RN-08).
 *
 * <p>La clave unica (id_sucursal, fecha) hace que el pase sea idempotente: si un dia ya corrio en
 * la sucursal, {@code PaseAlmonedaService.ejecutar()} no vuelve a tocar los contratos. Guarda el
 * conteo y el monto de prestamo pasado a venta para que el futuro modulo de boveda los consuma.</p>
 */
@Entity
@Table(name = "pase_almoneda", uniqueConstraints = @UniqueConstraint(
        name = "uq_pase_almoneda_sucursal_fecha", columnNames = {"id_sucursal", "fecha"}))
@Getter
@Setter
public class PaseAlmoneda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "id_sucursal", nullable = false)
    private Integer sucursalId;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "ejecutado_en", nullable = false)
    private LocalDateTime ejecutadoEn;

    @Column(name = "contratos_a_vencido", nullable = false)
    private Integer contratosAVencido = 0;

    @Column(name = "contratos_a_venta", nullable = false)
    private Integer contratosAVenta = 0;

    @Column(name = "monto_pasado_a_venta", nullable = false, precision = 18, scale = 2)
    private BigDecimal montoPasadoAVenta = BigDecimal.ZERO;
}
