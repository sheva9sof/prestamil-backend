package com.ignis.prestamil.response;

import com.ignis.prestamil.model.TipoMovimiento;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Detalle de un contrato en la pantalla de Finiquitos y Refrendos (GET /api/contratos/{id}/operacion):
 * la fila del listado más cliente, situación de periodos, partidas y último movimiento.
 */
@Getter
@Setter
public class ContratoOperacionDetalleResponse extends ContratoOperacionResponse {

    /** Fecha de empeño original; nunca cambia. */
    private LocalDateTime fechaApertura;
    private String telefonoCliente;
    private String nombreBeneficiario;

    // Situación a la fecha del servidor; null si el contrato ya no se puede cobrar
    private Integer diasAtraso;
    private Integer periodosTranscurridos;
    private Integer periodosNormales;
    private Integer periodosExtemporaneos;

    private List<PartidaContratoResponse> partidas;
    private UltimoMovimiento ultimoMovimiento;

    // Reposición/reimpresión de contrato (F9): configuración del plazo + importe ya calculado, para que
    // el modal muestre "% reposición" e "Importe" sin llamadas extra. Null si el plazo no la habilita.
    private Boolean cobrarReposicionContrato;
    private Boolean reposicionEsPorcentaje;
    private BigDecimal porcReposicion;
    private BigDecimal montoReposicion;
    private BigDecimal importeReposicion;

    /** Último movimiento no cancelado (RN-22). */
    @Getter
    @Setter
    public static class UltimoMovimiento {
        private Long id;
        private TipoMovimiento tipo;
        private LocalDateTime fecha;
        private BigDecimal monto;
        private String nombreUsuario;
    }
}
