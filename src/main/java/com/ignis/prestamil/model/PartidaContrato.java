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

    @Column(name = "peso_gramos", precision = 10, scale = 4)
    private BigDecimal pesoGramos;

    @Column(name = "kilataje")
    private Integer kilataje;

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
