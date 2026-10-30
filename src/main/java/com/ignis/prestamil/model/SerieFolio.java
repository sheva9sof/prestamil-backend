package com.ignis.prestamil.model;

/**
 * Series de folio consecutivo que emite {@code FolioService}. Cada serie lleva su propia numeración por
 * sucursal en {@code folio_secuencia}. Se guarda por nombre: no renombrar un valor existente, porque su
 * contador quedaría huérfano y la serie volvería a empezar en 1.
 */
public enum SerieFolio {
    NOTA_VENTA,
    TRASPASO_CAJA
}
