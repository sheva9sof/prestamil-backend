package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "direcciones")
@Getter
@Setter
public class Direccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_direccion", nullable = false, length = 20)
    private TipoDireccion tipoDireccion;

    @Column(name = "calle", length = 200, nullable = false)
    private String calle;

    @Column(name = "numero_exterior", length = 10, nullable = false)
    private String numeroExterior;

    @Column(name = "numero_interior", length = 10)
    private String numeroInterior;

    @Column(name = "colonia", length = 100, nullable = false)
    private String colonia;

    @Column(name = "ciudad", length = 100, nullable = false)
    private String ciudad;

    @Column(name = "estado", length = 50, nullable = false)
    private String estado;

    @Column(name = "codigo_postal", length = 5, nullable = false)
    private String codigoPostal;

    @Column(name = "referencias", columnDefinition = "TEXT")
    private String referencias;

    @Column(name = "es_verificada", columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean esVerificada = false;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDate fechaRegistro;

    public enum TipoDireccion {
        Fiscal,
        Particular,
        Laboral
    }

}

