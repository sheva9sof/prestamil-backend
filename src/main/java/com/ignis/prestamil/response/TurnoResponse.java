package com.ignis.prestamil.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TurnoResponse {
    private Integer id;
    private Integer usuarioId;
    private String nombreUsuario;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private Boolean activo;
}