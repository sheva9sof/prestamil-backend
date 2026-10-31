package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Saldo de arranque de caja de una sucursal (CAJ-06, B-04): el saldo de la bóveda del día de la migración,
 * que es el primer día con corte. Lo captura Sistemas una sola vez ({@code confirmado}); el que viene de
 * {@code configuraciones} (084) queda sin confirmar hasta entonces.
 *
 * <p>{@code fechaUltimoCierre} es el candado del cierre de día: todo día hasta esa fecha queda congelado.
 * Vive aquí, y no se calcula de {@code cierre_caja_diario}, para que las operaciones lo lean con bloqueo
 * de una sola fila.</p>
 */
@Entity
@Table(name = "caja_arranque")
@Getter
@Setter
public class CajaArranque {

    @Id
    @Column(name = "id_sucursal", nullable = false)
    private Integer sucursalId;

    @Column(name = "fecha_arranque", nullable = false)
    private LocalDate fechaArranque;

    @Column(name = "saldo_boveda", nullable = false, precision = 18, scale = 2)
    private BigDecimal saldoBoveda;

    /** {@code true} cuando Sistemas lo capturó; desde entonces ya no cambia. */
    @Column(name = "confirmado", nullable = false)
    private Boolean confirmado = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_captura")
    private Usuario usuarioCaptura;

    @Column(name = "fecha_captura")
    private LocalDateTime fechaCaptura;

    /** Último día cerrado; {@code null} si todavía no se cierra ninguno. */
    @Column(name = "fecha_ultimo_cierre")
    private LocalDate fechaUltimoCierre;
}
