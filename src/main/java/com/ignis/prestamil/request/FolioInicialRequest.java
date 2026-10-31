package com.ignis.prestamil.request;

import com.ignis.prestamil.model.SerieFolio;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

/**
 * Último folio que emitió COCAE en una serie de {@code FolioService} (CAJ-06): el siguiente que emita
 * Prestamil es este más uno.
 */
@Getter
@Setter
public class FolioInicialRequest {

    @NotNull(message = "La serie del folio es obligatoria")
    private SerieFolio serie;

    @NotNull(message = "El último folio es obligatorio")
    @PositiveOrZero(message = "El último folio no puede ser negativo")
    private Long ultimoFolio;
}
