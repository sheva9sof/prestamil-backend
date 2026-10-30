package com.ignis.prestamil.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Cuerpo de {@code POST /api/caja/traspasos/{id}/cancelar} (CAJ-02). Motivo libre y obligatorio, con las
 * mismas reglas que la cancelación de movimientos (RN-26).
 */
@Getter
@Setter
public class CancelarTraspasoCajaRequest {

    @NotBlank(message = "El motivo de cancelación es obligatorio")
    @Size(min = 10, max = 300, message = "El motivo debe tener entre 10 y 300 caracteres")
    private String motivo;
}
