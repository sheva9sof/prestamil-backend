package com.ignis.prestamil.util;

public class PrendaRow {

    private final String descripcion;
    private final String caracteristicas;
    private final String avaluo;
    private final String prestamo;
    private final String porcentajePrestamoSobreAvaluo;

    public PrendaRow(String descripcion, String caracteristicas, String avaluo, String prestamo,
                     String porcentajePrestamoSobreAvaluo) {
        this.descripcion = descripcion;
        this.caracteristicas = caracteristicas;
        this.avaluo = avaluo;
        this.prestamo = prestamo;
        this.porcentajePrestamoSobreAvaluo = porcentajePrestamoSobreAvaluo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getCaracteristicas() {
        return caracteristicas;
    }

    public String getAvaluo() {
        return avaluo;
    }

    public String getPrestamo() {
        return prestamo;
    }

    public String getPorcentajePrestamoSobreAvaluo() {
        return porcentajePrestamoSobreAvaluo;
    }
}

