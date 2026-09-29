package com.ignis.prestamil.model;

/**
 * Campo contra el que se compara el texto de búsqueda en la pantalla de Finiquitos y Refrendos (F2).
 */
public enum BuscarContratoPor {
    /** Número de contrato (id) o folio. */
    CONTRATO,
    /** Número de cliente. */
    NUM_CLIENTE,
    /** Nombre y apellidos del cliente; cada palabra debe aparecer en alguno de ellos. */
    NOMBRE_CLIENTE,
    /** Inicio del periodo vigente (RN-02), no la fecha de empeño. */
    FECHA_CONTRATO
}
