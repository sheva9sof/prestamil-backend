package com.ignis.prestamil.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class PlazoRequest {
    
    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotNull(message = "Los días por periodo son obligatorios")
    private Integer diasPorPeriodo;

    @NotNull(message = "El número de periodos es obligatorio")
    private Integer numeroPeriodos;

    private Boolean activo;

    private List<Integer> tiposPrenda = new ArrayList<>();

}

