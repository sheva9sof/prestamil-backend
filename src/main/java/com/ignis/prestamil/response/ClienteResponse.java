package com.ignis.prestamil.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClienteResponse {
    private Integer id;
    private String nombre;
    private String apellidoPaterno;
    private String apellidoMaterno;
    private String telefono;
    private String curp;
    private String nombreCompleto; // nombre + apellidoPaterno + apellidoMaterno
    private String rfc;
    private Boolean activo;
    private DireccionResponse direccion;
}

