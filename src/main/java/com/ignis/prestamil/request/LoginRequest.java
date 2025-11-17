package com.ignis.prestamil.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {
    private String nombreUsuario;
    private String password;
    private Integer sucursal;
}
