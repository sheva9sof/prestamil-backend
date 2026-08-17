package com.ignis.prestamil.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class SucursalResponse {
    private Integer id;
    private Integer numeroSucursal;
    private String nombre;
    private Integer idRazonSocial;
    private String nombreEmpresa;
    private String razonSocial;
    private String calle;
    private String noExterior;
    private String noInterior;
    private String colonia;
    private String municipio;
    private String cp;
    private String estado;
    private String pais;
    private Integer lada;
    private String telefono;
    private LocalDate fechaApertura;
    private String rfc;
    private LocalDateTime fechaRegistroProfeco;
    private LocalDateTime fechaContratoProfeco;
    private Boolean lunes;
    private Boolean martes;
    private Boolean miercoles;
    private Boolean jueves;
    private Boolean viernes;
    private Boolean sabado;
    private Boolean domingo;
    private String horarioAtencion;
}

