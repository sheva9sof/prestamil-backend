package com.ignis.prestamil.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Petición para recálculo server-side de partidas cuando el valuador cambia de plazo
 * en el avalúo (D-06). El servidor re-deriva cada partida con los parámetros del nuevo
 * plazo sin conservar valores del plazo anterior.
 */
@Getter
@Setter
public class RecalcularPartidasRequest {

    @NotNull
    private Long idPlazo;

    @NotEmpty
    @Valid
    private List<PartidaContratoRequest> partidas;
}
