package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "catalogo")
@Getter
@Setter
public class Catalogo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "nombre", length = 100, unique = true)
    private String nombre;

    @Column(name = "idTipoCatalogo")
    private Integer idTipoCatalogo;

    @Column(name = "valorCadena", length = 100)
    private String valorCadena;

    @Column(name = "valorNumerico", precision = 10, scale = 2)
    private BigDecimal valorNumerico;

    @Column(name = "valorFecha")
    private LocalDateTime valorFecha;

    @Column(name = "esEditable")
    private Short esEditable;

    @Column(name = "descrtipcion", length = 100)
    private String descrtipcion;

}

