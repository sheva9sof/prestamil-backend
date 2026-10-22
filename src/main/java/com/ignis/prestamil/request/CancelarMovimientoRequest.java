package com.ignis.prestamil.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Cuerpo de {@code POST /api/movimientos/{id}/cancelar}. El motivo es texto libre y obligatorio
 * (RN-26): con una lista cerrada los cajeros pondrían siempre lo mismo y la auditoría se quedaría sin
 * información útil.
 */
@Getter
@Setter
public class CancelarMovimientoRequest {

    @NotBlank(message = "El motivo de cancelación es obligatorio")
    @Size(min = 10, max = 300, message = "El motivo debe tener entre 10 y 300 caracteres")
    private String motivo;
}
