package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Último folio emitido de una serie en una sucursal (CAJ-01a). Contador genérico de {@code FolioService}
 * para las series nuevas; {@link FolioNota}, {@link FolioBolsa} y {@link FolioCodigoBarras} siguen con
 * sus tablas propias.
 */
@Entity
@Table(name = "folio_secuencia")
@Getter
@Setter
@IdClass(FolioSecuenciaId.class)
public class FolioSecuencia {

    @Id
    @Column(name = "id_sucursal", nullable = false)
    private Integer sucursalId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "serie", nullable = false, length = 30)
    private SerieFolio serie;

    @Column(name = "ultimo_folio", nullable = false)
    private Long ultimoFolio = 0L;
}
