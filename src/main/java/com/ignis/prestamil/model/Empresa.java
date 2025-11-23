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

}

