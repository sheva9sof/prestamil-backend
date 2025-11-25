package com.ignis.prestamil.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ParametrosSistemaRequest {
    private String descripcion;
    private String valorCadena;
    private BigDecimal valorNumerico;
    private String tipoDatoInterfaz;
}

