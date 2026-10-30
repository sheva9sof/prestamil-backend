package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Último número de bolsa asignado por sucursal (D-10). Es distinto del número de contrato.
 * <p>Pendiente de migrar a {@link FolioSecuencia} / {@code FolioService} (CAJ-01a); por ahora conserva su tabla.
 */
@Entity
@Table(name = "folio_bolsa")
@Getter
@Setter
public class FolioBolsa {

    @Id
    @Column(name = "id_sucursal", nullable = false)
    private Integer sucursalId;

    @Column(name = "ultimo_folio", nullable = false)
    private Integer ultimoFolio = 0;
}
