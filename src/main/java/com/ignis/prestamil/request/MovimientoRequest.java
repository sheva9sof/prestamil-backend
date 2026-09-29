package com.ignis.prestamil.request;

import com.ignis.prestamil.model.TipoOperacion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Registro de un movimiento con cobro (POST /api/movimientos). Solo trae lo que el cajero captura: el
 * servidor recalcula todos los montos con la fecha del servidor y nunca usa importes del cliente para
 * cobrar (RN-19).
 */
@Getter
@Setter
public class MovimientoRequest {

    @NotNull(message = "El contrato es obligatorio")
    private Long contratoId;

    @NotNull(message = "La operación es obligatoria")
    private TipoOperacion tipoOperacion;

    /** Periodos a cubrir; obligatorio solo en REFRENDO_PARCIAL. */
    private Integer periodos;

    /** Abono a capital; solo en ABONO_CAPITAL. */
    private BigDecimal abonoCapital;

    @NotNull(message = "La forma de pago es obligatoria")
    @Valid
    private PagoRequest pago;

    /**
     * Idempotencia: el frontend lo genera al abrir la ventana de Cobro. Si llega repetido (doble clic,
     * reintento tras un corte de red) se devuelve el movimiento ya registrado en vez de cobrar dos veces.
     */
    @NotBlank(message = "El identificador de la operación es obligatorio")
    @Size(max = 36, message = "El identificador de la operación admite máximo 36 caracteres")
    private String requestId;

    /**
     * Total que se le mostró al cajero. No se usa para cobrar: si el recálculo del servidor da otro
     * importe (p. ej. el contrato rebasó la gracia entre la cotización y el cobro) se rechaza para que
     * se vuelva a cotizar.
     */
    private BigDecimal totalCotizado;

    @Size(max = 300, message = "Las observaciones admiten máximo 300 caracteres")
    private String observaciones;
}
