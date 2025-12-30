package com.ignis.prestamil.model;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Getter
@Setter
public class PlazoParametroId implements Serializable {

    private Long plazoId;
    private Integer tipoPrendaId;

    public PlazoParametroId() {
    }

    public PlazoParametroId(Long plazoId, Integer tipoPrendaId) {
        this.plazoId = plazoId;
        this.tipoPrendaId = tipoPrendaId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlazoParametroId that = (PlazoParametroId) o;
        return Objects.equals(plazoId, that.plazoId) && Objects.equals(tipoPrendaId, that.tipoPrendaId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(plazoId, tipoPrendaId);
    }

}

