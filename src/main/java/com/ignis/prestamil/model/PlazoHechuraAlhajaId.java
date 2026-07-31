package com.ignis.prestamil.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlazoHechuraAlhajaId implements Serializable {

    @Column(name = "id_plazo", nullable = false)
    private Integer idPlazo;

    @Column(name = "sucursal_id", nullable = false)
    private Integer sucursalId;

    @Column(name = "kilataje", nullable = false)
    private Integer kilataje;

    @Column(name = "hechura", nullable = false, length = 1)
    private String hechura; // "F" fina, "N" normal, "E" especial

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlazoHechuraAlhajaId that)) return false;
        return Objects.equals(idPlazo, that.idPlazo)
            && Objects.equals(sucursalId, that.sucursalId)
            && Objects.equals(kilataje, that.kilataje)
            && Objects.equals(hechura, that.hechura);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idPlazo, sucursalId, kilataje, hechura);
    }
}
