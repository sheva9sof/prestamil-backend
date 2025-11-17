package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @CreationTimestamp
    @Column(name = "creado", nullable = false, updatable = false)
    private LocalDateTime creado;

    @Column(name = "nombreUsuario", length = 30, nullable = false, insertable = true, updatable = true)
    private String nombreUsuario;

    @Column(name = "password", length = 100, nullable = false)
    private String password;

    @Column(name = "nombre", length = 100, nullable = false)
    private String nombre;

    @Column(name = "apellidos", length = 100, nullable = false)
    private String apellidos;

    @Column(name = "estatus", nullable = false)
    private Boolean estatus;

    @Column(name = "cambiarPassword", nullable = false)
    private Boolean cambiarPassword;

    @Column(name = "ultimoLogin")
    private LocalDateTime ultimoLogin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idRol", nullable = false)
    private Rol rol;

    @Column(name = "fechaIni")
    private LocalDate fechaIni;

    @Column(name = "fechaFin")
    private LocalDate fechaFin;

    @Column(name = "vigencia", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean vigencia = false;

    @Column(name = "aplicaCambioPassword", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 1")
    private Boolean aplicaCambioPassword = true;

    @Column(name = "fechaCambioPass", nullable = false)
    private LocalDate fechaCambioPass;

    @Column(name = "editable", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 1")
    private Boolean editable = true;

    @Column(name = "session_token", length = 100)
    private String sessionToken;

    @Column(name = "ultima_actividad")
    private LocalDateTime ultimaActividad;

    @Column(name = "inicio_sesion")
    private LocalDateTime inicioSesion;
}

