package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "contrato")
@Getter
@Setter
public class Contrato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "folio", length = 20, unique = true)
    private String folio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_turno", nullable = false)
    private Turno turno;

    @Column(name = "id_sucursal", nullable = false)
    private Integer sucursalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_plazo", nullable = false)
    private Plazo plazo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_beneficiario")
    private Cliente beneficiario;

    @Column(name = "nombre_beneficiario", length = 200)
    private String nombreBeneficiario;

    @Column(name = "tipo_identificacion", length = 60)
    private String tipoIdentificacion;

    @Column(name = "num_identificacion", length = 30)
    private String numIdentificacion;

    @Column(name = "fecha_apertura", nullable = false)
    private LocalDateTime fechaApertura;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Column(name = "monto_prestamo", nullable = false, precision = 18, scale = 2)
    private BigDecimal montoPrestamo;

    @Column(name = "monto_avaluo", nullable = false, precision = 18, scale = 2)
    private BigDecimal montoAvaluo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estatus", nullable = false, length = 20)
    private EstatusContrato estatus = EstatusContrato.VIGENTE;

    @Column(name = "num_refrendos", nullable = false)
    private Integer numRefrendos = 0;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    @OneToMany(mappedBy = "contrato", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PartidaContrato> partidas = new ArrayList<>();
}
