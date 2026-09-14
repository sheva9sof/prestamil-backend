package com.ignis.prestamil.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CatValorPrendaRequest {

    @NotNull
    @Min(1)
    private Integer idTipoPrenda;

    @NotNull
    @Min(1)
    private Integer idAtributo;

    /**
     * Nombre estándar del catálogo. Opcional: el modal de alta ya no lo captura
     * (el ítem se identifica por clave). Si llega null, el servicio conserva el
     * valor existente en lugar de borrarlo.
     */
    @Size(max = 100)
    private String descripcion;

    @Size(max = 20)
    private String clave;

    @Min(0)
    private Integer kilataje;

    private Boolean contienePiedad;
}
