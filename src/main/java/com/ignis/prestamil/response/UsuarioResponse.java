package com.ignis.prestamil.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class UsuarioResponse {
    private Integer id;
    private String nombreUsuario;
    private String nombre;
    private String apellidos;
    private Boolean estatus;
    private Boolean cambiarPassword;
    private LocalDateTime ultimoLogin;
    private Integer idRol;
    private LocalDate fechaIni;
    private LocalDate fechaFin;
    private Boolean vigencia;
    private Boolean aplicaCambioPassword;
    private LocalDate fechaCambioPass;
    private Boolean editable;
    private String sessionToken;
    private LocalDateTime ultimaActividad;
    private LocalDateTime inicioSesion;
    private LocalDateTime creado;
}

