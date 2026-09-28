package com.ignis.prestamil.util;

import java.math.BigDecimal;

public class Constantes {

    // Parámetros generales del sistema
    public static final int AVISO_CONTRATO = 1;
    public static final int AVISO_PRIVACIDAD = 2;
    public static final int TOMAR_PRECIO_VENTA_DE_LA_TABLA = 3;
    public static final int FACTOR_INCREMENTO_PRECIO_VENTA_ALHAJAS = 4;
    public static final int FACTOR_INCREMENTO_PRECIO_VENTA_VARIOS = 5;
    public static final int DESCUENTO_VENTA_PUBLICO_ALHAJAS = 6;
    public static final int DESCUENTO_VENTA_PUBLICO_VARIOS = 7;
    public static final int IVA = 8;
    public static final int PRESTAMO_MAXIMO_POR_CONTRATO = 9;
    public static final int IMPRIMIR_CODIGO_DE_BARRAS = 10;
    public static final int IMPRIME_CARTA_RESPONSIVA = 11;
    public static final int DIAS_CONVENIO_APARTADOS = 12;
    public static final int PORCENTAJE_MINIMO_APARTADOS = 13;
    public static final int IMPORTE_MINIMO_APARTADOS = 14;
    public static final int PORCENTAJE_SANCION_APARTADOS = 15;

    // Nombres de configuraciones y menús
    public static final String ROLES_PERMITIDOS_APERTURA_TURNOS = "ROLES_PERMITIDOS_APERTURA_TURNOS";
    public static final String NOMBRE_MENU_TURNO = "Turnos";

    // Contratos
    // Fecha de comercialización = vencimiento + 15 días (RN-08, confirmado con 4 contratos COCAE).
    // Cuadra con plazo_parametro.dias_antes_pase_venta = 14 (último día de pago = venc + 14) en los
    // plazos semanales de dev; el plazo quincenal lo tiene en 0, por eso no se lee de ahí todavía.
    public static final int DIAS_VENCIMIENTO_A_COMERCIALIZACION = 15;

    // Abono a capital mínimo (RN-13, confirmado con Jorge 21-sep-2026)
    public static final BigDecimal ABONO_CAPITAL_MINIMO = new BigDecimal("20.00");
}