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

    /**
     * Igualdad basada en id (patrón recomendado para entidades JPA usadas en
     * colecciones Set): necesario para que Hibernate pueda calcular el diff real
     * de Plazo.tiposPrenda (Set) y no borre/reinserte toda la fila cuando el
     * conjunto de tipos de prenda no cambió. hashCode constante evita que el
     * elemento "se mueva de bucket" al pasar de transitorio a persistente.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TipoPrenda)) {
            return false;
        }
        TipoPrenda other = (TipoPrenda) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}

