package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Registro de auditoría. La cancelación de movimientos (F10) escribe aquí el estado anterior y el
 * nuevo del contrato para poder reconstruir qué se revirtió. El schema viene del initial-schema.
 */
@Entity
@Table(name = "bitacora")
@Getter
@Setter
public class Bitacora {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "nombreUsuario", nullable = false, length = 30)
    private String nombreUsuario;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @Column(name = "valorViejo", length = 5000)
    private String valorViejo;

    @Column(name = "valorNuevo", nullable = false, length = 5000)
    private String valorNuevo;

    @Column(name = "tipoMov", nullable = false, length = 100)
    private String tipoMov;
}
