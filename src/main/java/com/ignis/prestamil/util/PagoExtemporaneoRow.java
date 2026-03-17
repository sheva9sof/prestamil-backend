package com.ignis.prestamil.util;

public class PagoExtemporaneoRow {

    private final String concepto;
    private final String importeMutuo;
    private final String intereses;
    private final String almacenaje;
    private final String iva;
    private final String porDesempeno;
    private final String cuandoSeRealizaPago;
    private final String porRefrendo;

    public PagoExtemporaneoRow(String concepto, String importeMutuo, String intereses,
                               String almacenaje, String iva, String porDesempeno,
                               String cuandoSeRealizaPago, String porRefrendo) {
        this.concepto = concepto;
        this.importeMutuo = importeMutuo;
        this.intereses = intereses;
        this.almacenaje = almacenaje;
        this.iva = iva;
        this.porDesempeno = porDesempeno;
        this.cuandoSeRealizaPago = cuandoSeRealizaPago;
        this.porRefrendo = porRefrendo;
    }

    public String getConcepto() {
        return concepto;
    }

    public String getImporteMutuo() {
        return importeMutuo;
    }

    public String getIntereses() {
        return intereses;
    }

    public String getAlmacenaje() {
        return almacenaje;
    }

    public String getIva() {
        return iva;
    }

    public String getPorDesempeno() {
        return porDesempeno;
    }

    public String getCuandoSeRealizaPago() {
        return cuandoSeRealizaPago;
    }

    public String getPorRefrendo() {
        return porRefrendo;
    }
}

