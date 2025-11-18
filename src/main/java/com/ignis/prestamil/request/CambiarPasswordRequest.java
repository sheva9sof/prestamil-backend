package com.ignis.prestamil.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CambiarPasswordRequest {
    private String passwordActual;
    private String passwordNueva;
}

