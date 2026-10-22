package com.ignis.prestamil.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Reposición/reimpresión de contrato (F9, POST /api/movimientos/reposicion/{contratoId}). El importe lo
 * calcula el servidor con {@code porc_reposicion} o {@code monto_reposicion} del plazo. La casilla
 * "No cobrar la reposición del contrato" solo la aceptan los roles configurados (por defecto Gerente y
 * Sistemas); el resto de usuarios debe cobrar y el backend responde 403 si otro rol la envía.
 */
@Getter
@Setter
public class ReposicionRequest {

    /** Exenta el cobro (importe 0); requiere rol permitido y siempre deja un movimiento RE registrado. */
    private boolean noCobrar;

    /** Motivo o nota libre; se guarda en {@code observaciones} para auditoría. */
    @Size(max = 300, message = "El comentario admite máximo 300 caracteres")
    private String comentario;

    /** Forma de pago capturada en la ventana de Cobro; obligatoria solo si el importe {@literal >} 0. */
    @Valid
    private PagoRequest pago;

    /** Idempotencia: doble clic o reintento con el mismo id devuelve el movimiento ya registrado. */
    @NotBlank(message = "El identificador de la operación es obligatorio")
    @Size(max = 36, message = "El identificador de la operación admite máximo 36 caracteres")
    private String requestId;
}
