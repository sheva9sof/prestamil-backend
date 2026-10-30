package com.ignis.prestamil.model;

/**
 * Renglones del corte de caja, en el mismo orden que el PDF de COCAE (sección 3 de
 * {@code PLAN-CORTE-CAJA.md}). Algunos se alimentan de {@code movimiento_caja} vía {@link ConceptoCaja},
 * otros de {@code traspaso_caja} (DOTACIONES, RECOLECCIÓN) y otros todavía no tienen flujo y valen 0
 * (INDEMNIZACIONES, APARTADOS).
 */
public enum RenglonCorte {
    DOTACIONES("DOTACIONES", Columna.INGRESO),
    EMPENOS("EMPEÑOS", Columna.EGRESO),
    CANCELACION_EMPENOS("CANCELACIÓN DE EMPEÑOS", Columna.INGRESO),
    INDEMNIZACIONES("INDEMNIZACIONES", Columna.EGRESO),
    DEVOLUCIONES("DEVOLUCIONES", Columna.EGRESO),
    DEMASIAS("DEMASÍAS", Columna.EGRESO),
    ABONOS_PARCIALES("ABONOS PARCIALES", Columna.INGRESO),
    ABONOS_CAPITAL("ABONOS A CAPITAL", Columna.INGRESO),
    APARTADOS("APARTADOS / ABONOS DE APARTADOS", Columna.INGRESO),
    FINIQUITOS("FINIQUITOS", Columna.INGRESO),
    FINIQUITOS_EXTEMPORANEOS("FINIQUITOS EXTEMPORÁNEOS", Columna.INGRESO),
    REFRENDOS("REFRENDOS", Columna.INGRESO),
    REFRENDOS_EXTEMPORANEOS("REFRENDOS EXTEMPORÁNEOS", Columna.INGRESO),
    REPOSICIONES("REPOSICIONES", Columna.INGRESO),
    VENTAS("VENTAS", Columna.INGRESO),
    CANCELACIONES_VENTA("CANCELACIONES DE VENTA", Columna.EGRESO),
    RECOLECCION("RECOLECCIÓN", Columna.EGRESO);

    /** Columna del corte donde se suma el renglón. */
    public enum Columna { INGRESO, EGRESO }

    private final String etiqueta;
    private final Columna columna;

    RenglonCorte(String etiqueta, Columna columna) {
        this.etiqueta = etiqueta;
        this.columna = columna;
    }

    /** Texto del renglón en el corte impreso. */
    public String getEtiqueta() {
        return etiqueta;
    }

    public Columna getColumna() {
        return columna;
    }
}
