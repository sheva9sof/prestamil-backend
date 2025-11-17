package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "roles")
@Getter
@Setter
public class Rol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "rol", length = 100, nullable = false)
    private String rol;

    @Column(name = "estatus", nullable = false)
    private Boolean estatus;

    @OneToMany(mappedBy = "rol", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RolOpcion> rolOpciones = new ArrayList<>();

    /**
     * Obtiene la lista de Opciones asociadas a este Rol
     * @return Lista de Opcion
     */
    public List<Opcion> getOpciones() {
        return rolOpciones.stream()
                .map(RolOpcion::getOpcion)
                .collect(Collectors.toList());
    }

}

