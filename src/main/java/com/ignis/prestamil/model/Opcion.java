package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "opciones")
@Getter
@Setter
public class Opcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "opcion", length = 100, nullable = false)
    private String opcion;

    @Column(name = "estatus", nullable = false)
    private Boolean estatus;

    @Column(name = "principalMenu", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 1")
    private Boolean principalMenu = true;

    @Column(name = "permiso", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean permiso = false;

    @Column(name = "idPadre", nullable = true, insertable = true, updatable = true)
    private Integer idPadre;

    @Column(name = "icono", nullable = false)
    private Boolean icono;

    @Column(name = "nombreIcono", length = 100)
    private String nombreIcono;

}

