package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Fila de detalle de un pase de almoneda (C-11). Una por contrato que paso a VENCIDO y una por
 * cada partida que paso a EN_VENTA. La pantalla "Resultados del pase de almoneda" lo consume para
 * imprimir / exportar las dos pestañas.
 */
@Entity
@Table(name = "pase_almoneda_detalle")
@Getter
@Setter
public class PaseAlmonedaDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_pase", nullable = false)
    private PaseAlmoneda pase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_contrato", nullable = false)
    private Contrato contrato;

    /** Partida que paso a EN_VENTA; NULL en las filas VENCIDO (el cambio es del contrato). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_partida")
    private PartidaContrato partida;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_cambio", nullable = false, length = 10)
    private TipoCambioPase tipoCambio;
}
