package com.ignis.prestamil.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class SucursalRequest {
    private Integer numeroSucursal;
    private String nombre;
    private Integer idRazonSocial;
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
    
    @NotNull(message = "Fecha Registro PROFECO es obligatoria")
    private LocalDateTime fechaRegistroProfeco;
    
    @NotNull(message = "Fecha Contrato PROFECO es obligatoria")
    private LocalDateTime fechaContratoProfeco;
    
    private Boolean lunes;
    private Boolean martes;
    private Boolean miercoles;
    private Boolean jueves;
    private Boolean viernes;
    private Boolean sabado;
    private Boolean domingo;
}

