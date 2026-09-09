package com.ignis.prestamil.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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

    @NotBlank
    @Size(max = 100)
    private String descripcion;

    @Size(max = 20)
    private String clave;

    @Min(0)
    private Integer kilataje;

    private Boolean contienePiedad;
}
