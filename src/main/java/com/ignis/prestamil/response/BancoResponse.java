package com.ignis.prestamil.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BancoResponse {
    private Integer id;
    private String nombre;
    private Boolean activo;
}
