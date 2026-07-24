package com.ignis.prestamil.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class OroCeldaUpdateRequest {

    @NotNull
    @PositiveOrZero
    private BigDecimal porcPrestamo;
}
