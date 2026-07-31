package com.ignis.prestamil.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class LoginResponse {
    private Integer id;
    private String username;
    private String password;
    private String nombre;
    private String apellidos;
    private Boolean estatus;
    private Boolean cambiarPassword;
    private LocalDateTime ultimoLogin;
    private Integer idRol;
    private String rolNombre;
    private LocalDate fechaIni;
    private LocalDate fechaFin;
    private Boolean vigencia;
    private Boolean aplicaCambioPassword;
    private LocalDate fechaCambioPass;
    private Boolean editable;
    private LocalDateTime ultimaActividad;
    private LocalDateTime inicioSesion;
    private List<MenuResponse> opciones;
    private int sessionTimeoutMinutes = 30;
    private int warningMinutes = 3;
}
