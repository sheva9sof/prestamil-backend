package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "nombreUsuario", nullable = false, length = 30, unique = true)
    private String nombreUsuario;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String apellidos;

    @Column(nullable = false)
    private boolean estatus;

    @Column(name = "cambiarPassword", nullable = false)
    private boolean cambiarPassword;

    @Column(nullable = false)
    private LocalDateTime creado;

    @Column(name = "ultimoLogin")
    private LocalDateTime ultimoLogin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idRol", nullable = false)
    private Rol rol;

    @Column(name = "fechaIni")
    private LocalDate fechaIni;

    @Column(name = "fechaFin")
    private LocalDate fechaFin;

    @Column(nullable = false)
    private boolean vigencia;

    @Column(name = "aplicaCambioPassword", nullable = false)
    private boolean aplicaCambioPassword;

    @Column(name = "fechaCambioPass", nullable = false)
    private LocalDate fechaCambioPass;

    @Column(nullable = false)
    private boolean editable;

    @Column(name = "ultima_actividad")
    private LocalDateTime ultimaActividad;

    @Column(name = "inicio_sesion")
    private LocalDateTime inicioSesion;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
