package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "parametros_sistema")
@Getter
@Setter
public class ParametrosSistema {

    @Id
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "descripcion", length = 100)
    private String descripcion;

    @Column(name = "valor_cadena", length = 1000)
    private String valorCadena;

    @Column(name = "valor_numerico", precision = 10, scale = 2)
    private BigDecimal valorNumerico;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_dato_interfaz", length = 20)
    private TipoDatoInterfaz tipoDatoInterfaz;

    public enum TipoDatoInterfaz {
        bool,
        textarea,
        text,
        number
    }

}
