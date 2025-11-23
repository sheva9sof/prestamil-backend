package com.ignis.prestamil.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class DireccionResponse {
    private Integer id;
    private String tipoDireccion;
    private String calle;
    private String numeroExterior;
    private String numeroInterior;
    private String colonia;
    private String ciudad;
    private String estado;
    private String codigoPostal;
    private String referencias;
    private Boolean esVerificada;
    private LocalDate fechaRegistro;
}

