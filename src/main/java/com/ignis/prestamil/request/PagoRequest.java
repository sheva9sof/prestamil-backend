package com.ignis.prestamil.request;

import com.ignis.prestamil.model.TipoTarjeta;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Forma de pago capturada en la ventana de Cobro (RN-24): efectivo, tarjeta o mixto. Los datos de la
 * tarjeta solo son obligatorios si {@code tarjeta > 0}; de la tarjeta nunca se recibe el número
 * completo, solo los últimos 4 dígitos (PCI DSS).
 */
@Getter
@Setter
public class PagoRequest {

    /** Efectivo que entrega el cliente; el cambio sale de aquí. Sin valor = 0. */
    @DecimalMin(value = "0.00", message = "El efectivo no puede ser negativo")
    @Digits(integer = 16, fraction = 2, message = "El efectivo admite máximo 2 decimales")
    private BigDecimal efectivo;

    /** Importe cargado a la tarjeta; no puede exceder el total. Sin valor = 0. */
    @DecimalMin(value = "0.00", message = "El importe con tarjeta no puede ser negativo")
    @Digits(integer = 16, fraction = 2, message = "El importe con tarjeta admite máximo 2 decimales")
    private BigDecimal tarjeta;

    private TipoTarjeta tipoTarjeta;

    @Pattern(regexp = "\\d{4}", message = "Capture solo los últimos 4 dígitos de la tarjeta")
    private String tarjetaUltimos4;

    private Integer bancoEmisorId;

    @Size(max = 30, message = "La autorización admite máximo 30 caracteres")
    private String autorizacion;
}
