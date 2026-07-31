package com.ignis.prestamil.response;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class UsuarioResponse {
    private Integer id;
    private String nombreUsuario;
    private String nombre;
    private String apellidos;
    private boolean estatus;
    private Integer idRol;
    private String rolNombre;
    private LocalDate fechaIni;
    private LocalDate fechaFin;
    private LocalDateTime ultimoLogin;
    private LocalDateTime creado;
}
