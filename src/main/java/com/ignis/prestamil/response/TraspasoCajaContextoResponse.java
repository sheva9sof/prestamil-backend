package com.ignis.prestamil.response;

import com.ignis.prestamil.model.TipoTraspasoCaja;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Lo que la pantalla de un traspaso muestra antes de capturar (CAJ-04): las cajas que fija el tipo
 * (RC-02), la fecha de operación del servidor y qué puede hacer el usuario de la sesión.
 */
@Getter
@Setter
public class TraspasoCajaContextoResponse {
    private TipoTraspasoCaja tipo;
    /** Hoy según el servidor; es la fecha con la que se guardará el traspaso. */
    private LocalDate fechaOperacion;
    private CajaResponse cajaOrigen;
    private CajaResponse cajaDestino;
    /** El rol del usuario está en la configuración de roles de este tipo. */
    private boolean puedeRegistrar;
    /** El rol del usuario está en {@code ROLES_PERMITIDOS_CANCELAR_TRASPASO}. */
    private boolean puedeCancelar;
    /** La caja de hoy ya se cerró (CAJ-06): no se registra ni se cancela nada. */
    private boolean diaCerrado;
}
