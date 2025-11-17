package com.ignis.prestamil.response;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class MenuResponse {
    
    private Integer id;
    private String opcion;
    private String icono;
    private String nombreIcono;
    private String url;
    private String descripcion;
    private List<MenuResponse> submenus;
    
    public MenuResponse() {
        this.submenus = new ArrayList<>();
    }
}
