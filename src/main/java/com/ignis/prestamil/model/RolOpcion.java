package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "roles_opciones")
@Getter
@Setter
@IdClass(RolOpcionId.class)
public class RolOpcion {

    @Id
    @Column(name = "idRol", nullable = false)
    private Integer idRol;

    @Id
    @Column(name = "idOpcion", nullable = false)
    private Integer idOpcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idRol", insertable = false, updatable = false)
    private Rol rol;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idOpcion", insertable = false, updatable = false)
    private Opcion opcion;

}

