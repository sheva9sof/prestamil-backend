package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.Usuario;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends BaseRepository<Usuario, Integer>, JpaSpecificationExecutor<Usuario> {

    Optional<Usuario> findByNombreUsuario(String nombreUsuario);

    boolean existsByNombreUsuario(String nombreUsuario);

    @EntityGraph(attributePaths = {"rol", "rol.rolOpciones", "rol.rolOpciones.opcion"})
    Optional<Usuario> findWithRolAndOpcionesByNombreUsuario(String nombreUsuario);

    @EntityGraph(attributePaths = {"rol", "rol.rolOpciones", "rol.rolOpciones.opcion"})
    Optional<Usuario> findWithRolAndOpcionesById(Integer id);

}
