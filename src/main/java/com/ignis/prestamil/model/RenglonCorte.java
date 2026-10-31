package com.ignis.prestamil.model;

/**
 * Renglones del corte de caja, en el mismo orden que el PDF de COCAE (sección 3 de
 * {@code PLAN-CORTE-CAJA.md}). Algunos se alimentan de {@code movimiento_caja} vía {@link ConceptoCaja},
 * otros de {@code traspaso_caja} (DOTACIONES, RECOLECCIÓN) y otros todavía no tienen flujo y valen 0
 * (INDEMNIZACIONES, DEVOLUCIONES, ABONOS PARCIALES, APARTADOS).
 *
 * <p>Las cancelaciones no tienen renglón (Q-C1, Jorge 2026-10-30): restan del renglón de la operación
 * original y solo se listan en el reporte de cancelados (REP-03). Ver {@code ResolutorRenglonCorte}.</p>
 */
public enum RenglonCorte {
    DOTACIONES("DOTACIONES", Columna.INGRESO),
    EMPENOS("EMPEÑOS", Columna.EGRESO),
    INDEMNIZACIONES("INDEMNIZACIONES", Columna.EGRESO),
    /** Por confirmar qué es en COCAE; sin flujo, vale 0. */
    DEVOLUCIONES("DEVOLUCIONES", Columna.EGRESO),
    DEMASIAS("DEMASÍAS", Columna.EGRESO),
    /** No existen como tal (Q-C3): RP y RPX son refrendos. Sin flujo, vale 0. */
    ABONOS_PARCIALES("ABONOS PARCIALES", Columna.INGRESO),
    ABONOS_CAPITAL("ABONOS A CAPITAL", Columna.INGRESO),
    APARTADOS("APARTADOS / ABONOS DE APARTADOS", Columna.INGRESO),
    FINIQUITOS("FINIQUITOS", Columna.INGRESO),
    FINIQUITOS_EXTEMPORANEOS("FINIQUITOS EXTEMPORÁNEOS", Columna.INGRESO),
    REFRENDOS("REFRENDOS", Columna.INGRESO),
    REFRENDOS_EXTEMPORANEOS("REFRENDOS EXTEMPORÁNEOS", Columna.INGRESO),
    REPOSICIONES("REPOSICIONES", Columna.INGRESO),
    VENTAS("VENTAS", Columna.INGRESO),
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
