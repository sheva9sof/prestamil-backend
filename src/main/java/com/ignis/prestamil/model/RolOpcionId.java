package com.ignis.prestamil.model;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Getter
@Setter
public class RolOpcionId implements Serializable {

    private Integer idRol;
    private Integer idOpcion;

    public RolOpcionId() {
    }

    public RolOpcionId(Integer idRol, Integer idOpcion) {
        this.idRol = idRol;
        this.idOpcion = idOpcion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RolOpcionId that = (RolOpcionId) o;
        return Objects.equals(idRol, that.idRol) && Objects.equals(idOpcion, that.idOpcion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idRol, idOpcion);
    }

}

