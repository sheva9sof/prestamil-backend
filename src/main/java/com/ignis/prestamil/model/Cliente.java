package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "clientes", 
       uniqueConstraints = @UniqueConstraint(name = "telefono", columnNames = {"telefono"}))
@Getter
@Setter
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "nombre", length = 100, nullable = false)
    private String nombre;

    @Column(name = "apellido_paterno", length = 150, nullable = false)
    private String apellidoPaterno;

    @Column(name = "apellido_materno", length = 150, nullable = false)
    private String apellidoMaterno;

    @Column(name = "telefono", length = 15, nullable = false, unique = true)
    private String telefono;

    @Column(name = "curp", length = 18)
    private String curp;

    @Column(name = "rfc", length = 13)
    private String rfc;

    @Column(name = "activo", columnDefinition = "TINYINT(1) DEFAULT 1")
    private Boolean activo = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "direccion_id", nullable = false)
    private Direccion direccion;

}

