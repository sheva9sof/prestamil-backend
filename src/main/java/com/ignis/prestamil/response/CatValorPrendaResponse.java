package com.ignis.prestamil.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CatValorPrendaResponse {
    private Integer idValorAtributo;
    private Integer idAtributo;
    private String nombreAtributo;
    private Integer idTipoPrenda;
    private String tipo;
    private Integer clave;
    private String descripcion;
    private Integer kilataje;
    private Boolean contienePiedad;
}
