package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "cat_valor_prenda")
@Getter
@Setter
public class CatValorPrenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_valor_atributo", nullable = false)
    private Integer idValorAtributo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_atributo", nullable = false)
    private CatSubtipoPrenda subtipoPrenda;

    /**
     * Clave estándar del ítem. Obligatoria pero NO única: varias prendas comparten clave y se
     * distinguen por descripción general y kilataje (D-09, ajuste del changeset 042).
     */
    @Column(name = "clave", length = 20, nullable = false)
    private String clave;

    /** Descripción general estandarizada ("ESCLAVA 14K"). Obligatoria desde D-09 (revierte 024). */
    @Column(name = "descripcion", length = 100, nullable = false)
    private String descripcion;

    @Column(name = "kilataje")
    private Integer kilataje;

    /** Si la prenda lleva piedra ("Con piedra?" de COCAE). */
    @Column(name = "contiene_piedra")
    private Boolean contienePiedra;

    /**
     * El changeset 041 le puso una clave o descripción provisional (vacía).
     * Se apaga cuando Sistemas edita el registro.
     */
    @Column(name = "pendiente_revision", nullable = false)
    private Boolean pendienteRevision = false;

}
