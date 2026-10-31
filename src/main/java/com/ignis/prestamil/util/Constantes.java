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
    // 16 y 17 estan en 003-session-params.sql (timeout de sesion y aviso previo).
    // C-04: default true = monto_reposicion fijo es IVA incluido ($50 = $43.10 + $6.90).
    // false = monto es base y se suma IVA encima ($50 = $50.00 + $8.00). G-03 pendiente con Jorge.
    public static final int REPOSICION_INCLUYE_IVA = 18;
    // D-14: % de sanción extemporánea semanal por default — se usa cuando al guardar un plazo
    // el request viene sin porcSancionSemanal. Fallback hard a 2 si el registro no existe
    // (ver ParametrosSistemaCache.getPorcSancionSemanalDefault).
    public static final int SANCION_SEMANAL_DEFAULT = 19;

    // Nombres de configuraciones y menús
    public static final String ROLES_PERMITIDOS_APERTURA_TURNOS = "ROLES_PERMITIDOS_APERTURA_TURNOS";
    // Roles que pueden marcar "No cobrar la reposición de contrato" (F9, Jorge WhatsApp 2026-09-26).
    // CSV de ids de rol; por defecto Gerente (5) y Sistemas (1). Cualquier otro rol → 403.
    public static final String ROLES_PERMITIDOS_EXENTAR_REPOSICION = "ROLES_PERMITIDOS_EXENTAR_REPOSICION";
    // Roles que pueden cancelar un movimiento (F10, RN-26, Jorge WhatsApp 2026-09-25).
    // CSV de ids de rol; por defecto Gerente (5). Cualquier otro rol → 403.
    public static final String ROLES_PERMITIDOS_CANCELAR_MOVIMIENTO = "ROLES_PERMITIDOS_CANCELAR_MOVIMIENTO";
    // Roles que pueden dar de alta, editar y borrar el catálogo de prendas (D-09, Q6).
    // CSV de ids de rol; por defecto solo Sistemas (1). Las sucursales solo consultan → 403.
    public static final String ROLES_PERMITIDOS_CATALOGO_PRENDAS = "ROLES_PERMITIDOS_CATALOGO_PRENDAS";
    // Roles que pueden consultar el pase de almoneda de otra fecha (OTR-01, Jorge 01:17:16).
    // CSV de ids de rol; por defecto solo Sistemas (1). El resto ve siempre el pase de hoy.
    public static final String ROLES_PERMITIDOS_PASE_ALMONEDA_OTRA_FECHA = "ROLES_PERMITIDOS_PASE_ALMONEDA_OTRA_FECHA";
    // Roles que pueden registrar cada traspaso entre cajas (CAJ-02, RC-02, Q-06). CSV de ids de rol;
    // por defecto ministración y concentración Gerente (5) y Sistemas (1); dotación y recolección
    // también el Cajero (3). Cualquier otro rol → 403.
    public static final String ROLES_PERMITIDOS_MINISTRACION = "ROLES_PERMITIDOS_MINISTRACION";
    public static final String ROLES_PERMITIDOS_CONCENTRACION = "ROLES_PERMITIDOS_CONCENTRACION";
    public static final String ROLES_PERMITIDOS_DOTACION = "ROLES_PERMITIDOS_DOTACION";
    public static final String ROLES_PERMITIDOS_RECOLECCION = "ROLES_PERMITIDOS_RECOLECCION";
    // Roles que pueden cancelar un traspaso entre cajas (CAJ-02). Por defecto solo Sistemas (1) hasta
    // que exista la autorización con credenciales (A-05).
    public static final String ROLES_PERMITIDOS_CANCELAR_TRASPASO = "ROLES_PERMITIDOS_CANCELAR_TRASPASO";
    // Roles que pueden consultar el corte de caja de otra fecha (CAJ-07a). CSV de ids de rol; por defecto
    // Gerente (5) y Sistemas (1). El cajero ve siempre el corte de hoy, igual que OTR-01.
    public static final String ROLES_PERMITIDOS_CORTE_CAJA_OTRA_FECHA = "ROLES_PERMITIDOS_CORTE_CAJA_OTRA_FECHA";
    public static final String NOMBRE_MENU_TURNO = "Turnos";

    // Contratos
    // D-09 / Q4: mínimo de caracteres de la descripción específica de cada partida (supuesto: 10,
    // pendiente de confirmar con Jorge).
    public static final int MIN_CARACTERES_DESCRIPCION_ESPECIFICA = 10;
    // Fecha de comercialización = vencimiento + 15 días (RN-08, confirmado con 4 contratos COCAE).
    // Cuadra con plazo_parametro.dias_antes_pase_venta = 14 (último día de pago = venc + 14) en los
    // plazos semanales de dev; el plazo quincenal lo tiene en 0, por eso no se lee de ahí todavía.
    public static final int DIAS_VENCIMIENTO_A_COMERCIALIZACION = 15;

    // Abono a capital mínimo (RN-13, confirmado con Jorge 21-sep-2026)
    public static final BigDecimal ABONO_CAPITAL_MINIMO = new BigDecimal("20.00");

    // Rol con permisos de configuracion sensible (descuento parametrizado, RN-27).
    // Ver seed en 002-initial-data.sql: (1, 'Sistemas', 1).
    public static final Integer ROL_SISTEMAS_ID = 1;
}