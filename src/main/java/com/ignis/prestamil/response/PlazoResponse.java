package com.ignis.prestamil.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class PlazoResponse {
    private Long id;
    private String nombre;
    private Integer diasPorPeriodo;
    private Integer numeroPeriodos;
    private Boolean activo;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;
    private List<TipoPrendaResponse> tiposPrenda = new ArrayList<>();
}

