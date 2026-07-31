package com.ignis.prestamil.response;

import com.ignis.prestamil.model.EstatusContrato;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class ContratoResponse {
    private Long id;
    private String folio;
    private Integer idCliente;
    private String nombreCliente;
    private Long idPlazo;
    private String nombrePlazo;
    private String tipoIdentificacion;
    private String numIdentificacion;
    private String nombreBeneficiario;
    private LocalDateTime fechaApertura;
    private LocalDate fechaVencimiento;
    private BigDecimal montoPrestamo;
    private BigDecimal montoAvaluo;
    private EstatusContrato estatus;
    private Integer numRefrendos;
    private LocalDateTime creadoEn;
    private List<PartidaContratoResponse> partidas;
}
