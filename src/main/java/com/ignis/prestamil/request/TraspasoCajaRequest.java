package com.ignis.prestamil.request;

import com.ignis.prestamil.model.TipoTraspasoCaja;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cuerpo de {@code POST /api/caja/traspasos} (CAJ-02). Las cajas de origen y destino salen del tipo
 * (RC-02) y la fecha de operación la pone el servidor; por eso no vienen aquí.
 */
@Getter
@Setter
public class TraspasoCajaRequest {

    @NotNull(message = "El tipo de traspaso es obligatorio")
    private TipoTraspasoCaja tipo;

    /** Total que muestra la pantalla; el servidor lo compara contra la suma del desglose (RC-03). */
    @NotNull(message = "El importe es obligatorio")
    @Positive(message = "El importe debe ser mayor a cero")
    private BigDecimal importe;

    @NotEmpty(message = "El desglose por denominación es obligatorio")
    @Valid
    private List<TraspasoCajaDenominacionRequest> desglose;

    /** Ficha bancaria de la concentración (Q-07), opcional. */
    @Size(max = 60, message = "La referencia bancaria admite máximo 60 caracteres")
    private String referenciaBancaria;
}
