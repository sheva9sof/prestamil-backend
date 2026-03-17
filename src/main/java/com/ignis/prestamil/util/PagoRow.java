package com.ignis.prestamil.util;

public class PagoRow {

    private final String numero;
    private final String importeMutuo;
    private final String intereses;
    private final String almacenaje;
    private final String iva;
    private final String porRefrendo;
    private final String porDesempeno;
    private final String cuandoSeRealizaPago;
    private final String tipoFila;

    public PagoRow(String numero, String importeMutuo, String intereses, String almacenaje, String iva,
                   String porRefrendo, String porDesempeno, String cuandoSeRealizaPago, String tipoFila) {
        this.numero = numero;
        this.importeMutuo = importeMutuo;
        this.intereses = intereses;
        this.almacenaje = almacenaje;
        this.iva = iva;
        this.porRefrendo = porRefrendo;
        this.porDesempeno = porDesempeno;
        this.cuandoSeRealizaPago = cuandoSeRealizaPago;
        this.tipoFila = tipoFila;
    }

    public String getNumero() {
        return numero;
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

    public String getPorRefrendo() {
        return porRefrendo;
    }

    public String getPorDesempeno() {
        return porDesempeno;
    }

    public String getCuandoSeRealizaPago() {
        return cuandoSeRealizaPago;
    }

    public String getTipoFila() {
        return tipoFila;
    }
}

