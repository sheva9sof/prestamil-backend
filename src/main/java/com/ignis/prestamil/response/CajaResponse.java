package com.ignis.prestamil.response;

import com.ignis.prestamil.model.TipoCaja;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CajaResponse {
    private Integer id;
    private Integer sucursalId;
    private TipoCaja tipo;
    private String clave;
    private String nombre;
    private Boolean activa;
}
