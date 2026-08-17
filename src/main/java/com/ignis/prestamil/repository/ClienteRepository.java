package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.Cliente;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClienteRepository extends BaseRepository<Cliente, Integer> {

    Optional<Cliente> findByTelefono(String telefono);

    boolean existsByTelefono(String telefono);

    /**
     * Busca clientes activos por nombre completo, teléfono, CURP o RFC.
     * @param searchTerm Término de búsqueda
     * @return Lista de clientes activos que coinciden con el término de búsqueda
     */
    @Query("SELECT c FROM Cliente c WHERE c.activo = true AND (" +
           "LOWER(CONCAT(c.nombre, ' ', c.apellidoPaterno, ' ', c.apellidoMaterno)) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(c.telefono) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(COALESCE(c.curp, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(COALESCE(c.rfc, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
           "ORDER BY c.nombre, c.apellidoPaterno, c.apellidoMaterno")
    List<Cliente> searchByNombreCompletoOrTelefono(@Param("searchTerm") String searchTerm);

}

