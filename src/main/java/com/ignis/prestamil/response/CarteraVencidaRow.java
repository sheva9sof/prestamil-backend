package com.ignis.prestamil.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Fila de la pestaña "Cartera vencida" (C-11). Columnas que el personal usa para llamar o mandar
 * WhatsApp al cliente.
 */
@Builder
public record CarteraVencidaRow(
        String folioContrato,
        String cliente,
        String direccion,
        String telefono,
        LocalDate fechaVencimiento,
        BigDecimal saldoCapital
) {
}
