package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tipo_prenda")
@Getter
@Setter
public class TipoPrenda {

    @Id
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "tipo", length = 50)
    private String tipo;

    @OneToMany(mappedBy = "tipoPrenda", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CatSubtipoPrenda> subtiposPrenda = new ArrayList<>();

}

