package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.Banco;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BancoRepository extends BaseRepository<Banco, Integer> {

    List<Banco> findAllByOrderByNombreAsc();

    /** Bancos que aparecen en el combo de la ventana de Cobro. */
    List<Banco> findByActivoTrueOrderByNombreAsc();

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Integer id);
}
