package com.ignis.prestamil.model;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Getter
@Setter
public class FolioSecuenciaId implements Serializable {

    private Integer sucursalId;
    private SerieFolio serie;

    public FolioSecuenciaId() {
    }

    public FolioSecuenciaId(Integer sucursalId, SerieFolio serie) {
        this.sucursalId = sucursalId;
        this.serie = serie;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FolioSecuenciaId that = (FolioSecuenciaId) o;
        return Objects.equals(sucursalId, that.sucursalId)
            && serie == that.serie;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sucursalId, serie);
    }
}
