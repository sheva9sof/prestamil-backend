package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "cat_valor_prenda")
@Getter
@Setter
public class CatValorPrenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_valor_atributo", nullable = false)
    private Integer idValorAtributo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_atributo", nullable = false)
    private CatSubtipoPrenda subtipoPrenda;

    @Column(name = "valor", length = 100, nullable = false)
    private String valor;

}

