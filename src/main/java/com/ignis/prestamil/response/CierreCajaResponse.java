package com.ignis.prestamil.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Un día de caja cerrado (CAJ-06).
 *
 * @param id           identificador del cierre
 * @param fecha        día cerrado
 * @param diaPendiente si lo cerró Sistemas después de su día
 * @param diferencia   arqueo de ventanilla al cerrar (RC-07)
 * @param saldoFinal   saldo de la bóveda al cerrar; es el saldo anterior del día siguiente (RC-08)
 * @param usuario      nombre de usuario de quien cerró
 * @param fechaCierre  cuándo se cerró
 */
public record CierreCajaResponse(
        Long id,
        LocalDate fecha,
        boolean diaPendiente,
        BigDecimal diferencia,
        BigDecimal saldoFinal,
        String usuario,
        LocalDateTime fechaCierre
) {
}
