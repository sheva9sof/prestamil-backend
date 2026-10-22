package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Último código de barras de partida emitido (D-10). Una sola fila ({@link #ID_UNICO}): el contador es
 * global para que el código no se repita entre sucursales.
 */
@Entity
@Table(name = "folio_codigo_barras")
@Getter
@Setter
public class FolioCodigoBarras {

    public static final Integer ID_UNICO = 1;

    @Id
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "ultimo_folio", nullable = false)
    private Long ultimoFolio = 0L;
}
