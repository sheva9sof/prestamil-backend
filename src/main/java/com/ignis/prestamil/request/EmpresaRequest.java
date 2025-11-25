package com.ignis.prestamil.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmpresaRequest {
    private String nombre;
    private String razonSocial;
    private String calle;
    private String noExterior;
    private String noInterior;
    private String colonia;
    private String delegacion;
    private String cp;
    private String estado;
    private String pais;
}

