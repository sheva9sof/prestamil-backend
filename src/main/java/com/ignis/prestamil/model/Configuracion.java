package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "configuraciones")
public class Configuracion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 100)
    private String configuracion;

    private String valorCadena;

    private Integer valorEntero;
}
