package com.ignis.prestamil.model;

/**
 * Botones de la pantalla de Finiquitos y Refrendos (columnas de la matriz RN-16). El frontend
 * solo habilita los que devuelve el backend.
 */
public enum AccionContrato {
    REFRENDO,
    FINIQUITO,
    ABONO_CAPITAL,
    REFRENDO_PARCIAL,
    REFRENDO_EXTEMPORANEO,
    FINIQUITO_EXTEMPORANEO,
    REPOSICION,
    CONSULTA,
    CANCELACION
}
