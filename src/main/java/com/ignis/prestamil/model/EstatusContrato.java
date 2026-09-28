package com.ignis.prestamil.model;

/**
 * Estatus persistido del contrato. EN_GRACIA no se persiste: se deriva de las fechas
 * (vencimiento < hoy <= vencimiento + dias de gracia). FINIQUITADO sustituye a DESEMPENADO (changeset 027).
 */
public enum EstatusContrato {
    VIGENTE, VENCIDO, EN_VENTA, VENDIDO, FINIQUITADO, CANCELADO
}
