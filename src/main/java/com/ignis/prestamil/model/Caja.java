package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Caja de la sucursal: lugar donde está el dinero (RC-01). Cada sucursal tiene 001 CUENTA CONCENTRADORA,
 * 002 BÓVEDA y 003 VENTANILLA (changeset 074).
 *
 * <p>Hoy hay un solo turno activo en todo el sistema (H-05), así que la sucursal tiene una sola
 * VENTANILLA y {@code turnos} todavía no guarda la caja sobre la que se abre el turno. Cuando haya
 * turnos simultáneos (B-05) se agregan más ventanillas y la relación turno–ventanilla.</p>
 */
@Entity
@Table(name = "caja", uniqueConstraints = @UniqueConstraint(
        name = "uq_caja_sucursal_clave", columnNames = {"id_sucursal", "clave"}))
@Getter
@Setter
public class Caja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "id_sucursal", nullable = false)
    private Integer sucursalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoCaja tipo;

    /** Clave de COCAE dentro de la sucursal (001, 002, 003). */
    @Column(name = "clave", nullable = false, length = 10)
    private String clave;

    @Column(name = "nombre", nullable = false, length = 60)
    private String nombre;

    @Column(name = "activa", nullable = false)
    private Boolean activa = true;
}
