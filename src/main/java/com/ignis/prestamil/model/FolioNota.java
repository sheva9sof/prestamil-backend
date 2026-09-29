package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Último folio de nota (ticket) emitido por sucursal (RN-25). Es distinto del número de contrato.
 */
@Entity
@Table(name = "folio_nota")
@Getter
@Setter
public class FolioNota {

    @Id
    @Column(name = "id_sucursal", nullable = false)
    private Integer sucursalId;

    @Column(name = "ultimo_folio", nullable = false)
    private Integer ultimoFolio = 0;
}
