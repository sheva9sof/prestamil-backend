package com.ignis.prestamil.service.calculo;

/**
 * Periodos que lleva un contrato en una fecha dada (RN-03, RN-04, RN-05). Base de la cotización
 * y de las acciones disponibles.
 *
 * @param diasTranscurridos      días desde la fecha de contrato vigente (puede ser negativo si el
 *                               periodo pagado por adelantado aún no empieza)
 * @param diasAtraso             días desde el vencimiento; 0 si no ha vencido
 * @param diasGraciaUsados       días de atraso perdonados por pagar dentro de la gracia (columna
 *                               "Dias G" de COCAE); 0 si no hay atraso o si la gracia se rebasó
 * @param extemporaneo           true si el atraso rebasa los días de gracia
 * @param periodosTranscurridos  periodos que cobra un refrendo completo (normales + extemporáneos)
 * @param periodosNormales       periodos dentro del plazo
 * @param periodosExtemporaneos  periodos posteriores al vencimiento
 * @param semanasSancion         semanas de sanción: {@code ceil(diasAtraso/7)} si es extemporáneo y el
 *                               plazo aplica sanción; 0 en otro caso
 */
public record SituacionPeriodos(
        int diasTranscurridos,
        int diasAtraso,
        int diasGraciaUsados,
        boolean extemporaneo,
        int periodosTranscurridos,
        int periodosNormales,
        int periodosExtemporaneos,
        int semanasSancion
) {}
