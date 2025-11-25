package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "empresa")
@Getter
@Setter
public class Empresa {

    @Id
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "nombre", length = 50)
    private String nombre;

    @Column(name = "razonSocial", length = 250)
    private String razonSocial;

    @Column(name = "calle", length = 100)
    private String calle;

    @Column(name = "no_exterior", length = 10)
    private String noExterior;

    @Column(name = "no_interior", length = 10)
    private String noInterior;

    @Column(name = "colonia", length = 10)
    private String colonia;

    @Column(name = "delegacion", length = 10)
    private String delegacion;

    @Column(name = "cp", length = 10)
    private String cp;

    @Column(name = "estado", length = 10)
    private String estado;

    @Column(name = "pais", length = 10)
    private String pais;

}

