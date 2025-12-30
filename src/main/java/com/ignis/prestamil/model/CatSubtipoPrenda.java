package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cat_subtipo_prenda")
@Getter
@Setter
public class CatSubtipoPrenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_atributo", nullable = false)
    private Integer idAtributo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tipo_prenda", nullable = false)
    private TipoPrenda tipoPrenda;

    @Column(name = "nombre_atributo", length = 50, nullable = false)
    private String nombreAtributo;

    @OneToMany(mappedBy = "subtipoPrenda", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CatValorPrenda> valoresPrenda = new ArrayList<>();

}

