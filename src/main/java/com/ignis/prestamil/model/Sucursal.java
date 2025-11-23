package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "sucursal")
@Getter
@Setter
public class Sucursal {

    @Id
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "nombre", length = 50)
    private String nombre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idRazonSocial", nullable = true)
    private Empresa empresa;

    @Column(name = "calle", length = 250)
    private String calle;

    @Column(name = "noExterior", length = 25)
    private String noExterior;

    @Column(name = "noInterior", length = 25)
    private String noInterior;

    @Column(name = "colonia", length = 250)
    private String colonia;

    @Column(name = "municipio", length = 250)
    private String municipio;

    @Column(name = "cp", length = 5)
    private String cp;

    @Column(name = "estado", length = 50)
    private String estado;

    @Column(name = "pais", length = 20)
    private String pais;

    @Column(name = "lada")
    private Integer lada;

    @Column(name = "telefono", length = 50)
    private String telefono;

    @Column(name = "fechaApertura")
    private LocalDate fechaApertura;

    @Column(name = "rfc", length = 20)
    private String rfc;

    @Column(name = "fechaRegistroProfeco")
    private LocalDateTime fechaRegistroProfeco;

    @Column(name = "fechaContratoProfeco")
    private LocalDateTime fechaContratoProfeco;

}

