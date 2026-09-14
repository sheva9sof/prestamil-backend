package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "partida_contrato")
@Getter
@Setter
public class PartidaContrato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_contrato", nullable = false)
    private Contrato contrato;

    @Column(name = "num_partida", nullable = false)
    private Integer numPartida;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tipo_prenda", nullable = false)
    private TipoPrenda tipoPrenda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_valor_prenda")
    private CatValorPrenda valorPrenda;

    @Column(name = "clave_prenda", length = 20)
    private String clavePrenda;

    @Column(name = "descripcion", length = 200, nullable = false)
    private String descripcion;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad = 1;

    /**
     * Peso del metal precioso en gramos (oro o plata puro, sin piedras ni soldadura).
     * Es el ÚNICO peso que entra en el cálculo de avalúo y préstamo.
     * Cuando la partida agrupa varias piezas (cantidad > 1) es el peso del LOTE completo,
     * no el unitario: el sistema nunca multiplica peso × cantidad.
     * NULL para Varios y Autos/Motos, que no se valúan por gramo.
     */
    @Column(name = "peso_neto", precision = 10, scale = 4)
    private BigDecimal pesoNeto;

    /**
     * Peso físico de la pieza completa en gramos, incluyendo piedras, plástico y soldadura.
     * Es informativo (para que el cliente sepa cuánto pesa realmente lo que empeña) y
     * NUNCA participa en el cálculo. Siempre {@code >= pesoNeto}; si el usuario no lo
     * captura, el servidor lo iguala al neto. NULL para Varios y Autos/Motos.
     */
    @Column(name = "peso_total", precision = 10, scale = 4)
    private BigDecimal pesoTotal;

    @Column(name = "kilataje")
    private Integer kilataje;

    @Column(name = "ley", precision = 9, scale = 4)
    private BigDecimal ley;

    @Column(name = "hechura", length = 5)
    private String hechura;

    @Column(name = "precio_x_gramo", precision = 12, scale = 4)
    private BigDecimal precioXGramo;

    @Column(name = "avaluo_real", nullable = false, precision = 18, scale = 2)
    private BigDecimal avaluoReal;

    @Column(name = "avaluo_contrato", nullable = false, precision = 18, scale = 2)
    private BigDecimal avaluoContrato;

    @Column(name = "monto_prestamo", nullable = false, precision = 18, scale = 2)
    private BigDecimal montoPrestamo;

    @Column(name = "subtipo", length = 50)
    private String subtipo;

    @Column(name = "marca", length = 80)
    private String marca;

    @Column(name = "modelo", length = 80)
    private String modelo;

    @Column(name = "serie_imei", length = 60)
    private String serieImei;

    @Column(name = "estado_fisico", length = 20)
    private String estadoFisico;
}
