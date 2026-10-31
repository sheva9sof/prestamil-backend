package com.ignis.prestamil.response;

/**
 * Si hoy se puede abrir turno según el cierre de caja (CAJ-06, B-04). No revisa el rol ni si ya hay un turno
 * abierto: eso lo valida la apertura.
 *
 * @param motivoBloqueo por qué no se puede abrir (la caja de hoy ya se cerró o un día quedó pendiente de
 *                      cierre); {@code null} si nada lo impide
 */
public record AperturaTurnoResponse(String motivoBloqueo) {
}
