package com.ignis.prestamil.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class CatalogoResponse {
    private Integer id;
    private String nombre;
    private Integer idTipoCatalogo;
    private String valorCadena;
    private BigDecimal valorNumerico;
    private LocalDateTime valorFecha;
    private Short esEditable;
    private String descrtipcion;
}

