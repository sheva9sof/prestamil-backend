package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.Cliente;
import com.ignis.prestamil.model.Direccion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ClienteRepositoryIT {

    @Autowired
    TestEntityManager em;

    @Autowired
    ClienteRepository clienteRepository;

    // -----------------------------------------------------------------------
    // Helper: persist a minimal Direccion (all nullable=false fields populated)
    // -----------------------------------------------------------------------

    private Direccion persistMinimalDireccion() {
        Direccion d = new Direccion();
        d.setTipoDireccion(Direccion.TipoDireccion.Particular);
        d.setCalle("Calle Prueba");
        d.setNumeroExterior("10");
        d.setColonia("Colonia Test");
        d.setCiudad("Ciudad Test");
        d.setEstado("CDMX");
        d.setCodigoPostal("06600");
        d.setFechaRegistro(LocalDate.now());
        d.setEsVerificada(false);
        return em.persistAndFlush(d);
    }

    // -----------------------------------------------------------------------
    // Helper: persist a Cliente with a fresh Direccion
    // -----------------------------------------------------------------------

    private Cliente persistCliente(String nombre, String apPat, String apMat, String tel) {
        return persistCliente(nombre, apPat, apMat, tel, null, null, true);
    }

    private Cliente persistCliente(String nombre, String apPat, String apMat, String tel,
                                    String curp, String rfc, boolean activo) {
        Direccion direccion = persistMinimalDireccion();
        Cliente c = new Cliente();
        c.setNombre(nombre);
        c.setApellidoPaterno(apPat);
        c.setApellidoMaterno(apMat);
        c.setTelefono(tel);
        c.setCurp(curp);
        c.setRfc(rfc);
        c.setActivo(activo);
        c.setDireccion(direccion);
        em.persist(c);
        em.flush();
        em.clear();
        return c;
    }

    // -----------------------------------------------------------------------
    // Tests — all use the JPQL LIKE query (no MATCH/AGAINST; H2 compatible)
    // -----------------------------------------------------------------------

    @Test
    void search_findsByPartialFirstName() {
        // Given
        persistCliente("Juan", "Garcia", "Lopez", "5512345678");

        // When
        List<Cliente> results = clienteRepository.searchByNombreCompletoOrTelefono("juan");

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getNombre()).isEqualToIgnoringCase("Juan");
    }

    @Test
    void search_findsByPartialPaternoSurname() {
        // Given
        persistCliente("Juan", "Garcia", "Lopez", "5512345679");

        // When
        List<Cliente> results = clienteRepository.searchByNombreCompletoOrTelefono("garcia");

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getApellidoPaterno()).isEqualToIgnoringCase("Garcia");
    }

    @Test
    void search_findsByTelefono() {
        // Given
        persistCliente("Ana", "Martinez", "Ruiz", "5512345680");

        // When
        List<Cliente> results = clienteRepository.searchByNombreCompletoOrTelefono("5512");

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTelefono()).startsWith("5512");
    }

    @Test
    void search_findsByCurp() {
        persistCliente("Laura", "Mendez", "Soto", "5512345683",
                "MESL900101MDFNTR01", "MESL900101AB1", true);

        List<Cliente> results = clienteRepository.searchByNombreCompletoOrTelefono("mdfntr01");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCurp()).isEqualTo("MESL900101MDFNTR01");
    }

    @Test
    void search_findsByRfc() {
        persistCliente("Luis", "Torres", "Diaz", "5512345684",
                "TODL910202HDFRZS02", "TODL910202CD2", true);

        List<Cliente> results = clienteRepository.searchByNombreCompletoOrTelefono("cd2");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getRfc()).isEqualTo("TODL910202CD2");
    }

    @Test
    void search_excludesInactiveClients() {
        persistCliente("Cliente", "Inactivo", "Prueba", "5512345685",
                null, null, false);

        List<Cliente> results = clienteRepository.searchByNombreCompletoOrTelefono("inactivo");

        assertThat(results).isEmpty();
    }

    @Test
    void search_returnsEmpty_whenNoMatch() {
        // Given
        persistCliente("Pedro", "Ramirez", "Cruz", "5512345681");

        // When
        List<Cliente> results = clienteRepository.searchByNombreCompletoOrTelefono("xyzzy");

        // Then
        assertThat(results).isEmpty();
    }

    @Test
    void search_isCaseInsensitive() {
        // Given
        persistCliente("Juan", "Garcia", "Lopez", "5512345682");

        // When
        List<Cliente> results = clienteRepository.searchByNombreCompletoOrTelefono("JUAN");

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getNombre()).isEqualToIgnoringCase("Juan");
    }
}
