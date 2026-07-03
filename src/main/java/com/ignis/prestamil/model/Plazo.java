package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "plazo")
@Getter
@Setter
public class Plazo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "nombre", length = 80, nullable = false)
    private String nombre;

    @Column(name = "dias_por_periodo", nullable = false)
    private Integer diasPorPeriodo;

    @Column(name = "numero_periodos", nullable = false)
    private Integer numeroPeriodos;

    @Column(name = "activo", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 1")
    private Boolean activo = true;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    @ManyToMany(fetch = FetchType.EAGER,
        cascade = {
            CascadeType.PERSIST,
            CascadeType.MERGE
        }
    )
    @JoinTable(
        name = "plazo_prenda",
        joinColumns = @JoinColumn(name = "plazo_id", nullable = false),
        inverseJoinColumns = @JoinColumn(name = "tipo_prenda_id", nullable = false)
    )
    private Set<TipoPrenda> tiposPrenda = new LinkedHashSet<>();

}

