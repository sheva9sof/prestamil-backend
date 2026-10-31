package com.ignis.prestamil.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Cuerpo de {@code PUT /api/caja/arranque} (CAJ-06): lo captura Sistemas una sola vez por sucursal, el día de
 * la migración.
 */
@Getter
@Setter
public class ArranqueCajaRequest {

    /** Día de la migración: primer día con corte. */
    @NotNull(message = "La fecha de arranque es obligatoria")
    private LocalDate fecha;

    /** Saldo de la bóveda al abrir el día de arranque. */
    @NotNull(message = "El saldo de la bóveda es obligatorio")
    @DecimalMin(value = "0.00", message = "El saldo de la bóveda no puede ser negativo")
    @Digits(integer = 16, fraction = 2, message = "El saldo de la bóveda admite hasta 2 decimales")
    private BigDecimal saldoBoveda;

    /** Último folio que emitió COCAE en cada serie; la numeración sigue desde ahí. */
    @Valid
    private List<FolioInicialRequest> folios = new ArrayList<>();
}
