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
     * Busca clientes por nombre completo (nombre, apellido_paterno, apellido_materno) o teléfono
     * @param searchTerm Término de búsqueda que se busca en los campos de nombre completo o teléfono
     * @return Lista de clientes que coinciden con el término de búsqueda
     */
    @Query("SELECT c FROM Cliente c WHERE " +
           "LOWER(CONCAT(c.nombre, ' ', c.apellidoPaterno, ' ', c.apellidoMaterno)) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(c.telefono) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<Cliente> searchByNombreCompletoOrTelefono(@Param("searchTerm") String searchTerm);

}

