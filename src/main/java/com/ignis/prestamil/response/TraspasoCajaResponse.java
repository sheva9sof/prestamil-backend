package com.ignis.prestamil.response;

import com.ignis.prestamil.model.EstatusTraspasoCaja;
import com.ignis.prestamil.model.TipoTraspasoCaja;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Traspaso entre cajas con su desglose (RC-02, RC-03). Alimenta la lista del día y el ticket.
 */
@Getter
@Setter
public class TraspasoCajaResponse {
    private Long id;
    private Integer sucursalId;
    private Long folio;
    private TipoTraspasoCaja tipo;

    private Integer cajaOrigenId;
    private String cajaOrigenClave;
    private String cajaOrigenNombre;
    private Integer cajaDestinoId;
    private String cajaDestinoClave;
    private String cajaDestinoNombre;

    private BigDecimal importe;
    private LocalDate fechaOperacion;
    private LocalDateTime fechaRegistro;
    private Integer usuarioId;
    private String nombreUsuario;
    private Integer turnoId;
    private String referenciaBancaria;

    // Cancelación: el traspaso nunca se borra, queda marcado.
    private EstatusTraspasoCaja estatus;
    private LocalDateTime fechaCancelacion;
    private String nombreUsuarioCancela;
    private String motivoCancelacion;

    private List<TraspasoCajaDenominacionResponse> desglose;
}
