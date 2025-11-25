package com.ignis.prestamil.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ParametrosSistemaResponse {
    private Integer id;
    private String descripcion;
    private String valorCadena;
    private BigDecimal valorNumerico;
    private String tipoDatoInterfaz;
}

