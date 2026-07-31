package com.ignis.prestamil.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ContratoRequest {

    @NotNull
    private Integer idCliente;

    @NotNull
    private Long idPlazo;

    private Integer idBeneficiario;

    /** Nombre del beneficiario (opcional). */
    private String nombreBeneficiario;
    private String tipoIdentificacion;
    private String numIdentificacion;

    @NotNull
    @NotEmpty
    @Valid
    private List<PartidaContratoRequest> partidas;
}
