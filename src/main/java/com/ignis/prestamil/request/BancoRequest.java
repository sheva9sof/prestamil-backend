package com.ignis.prestamil.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Alta o edición de un banco emisor del catálogo de la ventana de Cobro (RN-24).
 */
@Getter
@Setter
public class BancoRequest {

    @NotBlank(message = "El nombre del banco es obligatorio")
    @Size(max = 60, message = "El nombre del banco admite máximo 60 caracteres")
    private String nombre;

    /** Sin valor = activo. Un banco inactivo no aparece en la ventana de Cobro pero conserva su historial. */
    private Boolean activo;
}
