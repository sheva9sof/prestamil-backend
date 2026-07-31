package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.Plazo;
import com.ignis.prestamil.model.PlazoParametro;
import com.ignis.prestamil.model.TipoPrenda;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Reproduce (y verifica el fix de) el error reportado al editar un Plazo y Periodo:
 *
 * "Cannot delete or update a parent row: a foreign key constraint fails
 *  (plazo_parametro, CONSTRAINT fk_pp_plazo_categoria FOREIGN KEY (plazo_id, tipo_prenda_id)
 *  REFERENCES plazo_prenda (plazo_id, tipo_prenda_id))"
 *
 * Hibernate genera el schema de test desde las entidades (ddl-auto=create-drop), por lo que
 * la restricción fk_pp_plazo_categoria — definida en producción vía SQL crudo en el changeset
 * Liquibase 001/006, sin anotación JPA equivalente — no existe en el H2 de pruebas. Este test
 * la agrega manualmente para replicar fielmente el esquema real antes de ejercitar el mismo
 * patrón de persistencia que usa PlazoService.update().
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlazoRepositoryIT {

    @Autowired
    TestEntityManager em;

    @Autowired
    PlazoRepository plazoRepository;

    @Autowired
    DataSource dataSource;

    // DDL en H2 hace commit implícito y @BeforeAll corre fuera de la transacción
    // por-test de @DataJpaTest, así que se usa una Connection JDBC plana (no el
    // TestEntityManager, que exige estar dentro de una transacción activa) para
    // agregar UNA sola vez, para toda la clase, la restricción real que falta.
    @BeforeAll
    void addRealCompositeForeignKey() throws Exception {
        // plazo_prenda no tiene PK compuesta por defecto en el schema generado por Hibernate
        // para @JoinTable (a diferencia del changeset 001, que sí la define) — se agrega aquí
        // como índice único para poder referenciarla desde una FK, igual que en producción.
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate("ALTER TABLE plazo_prenda ADD CONSTRAINT ux_plazo_prenda_pk UNIQUE (plazo_id, tipo_prenda_id)");
            st.executeUpdate("ALTER TABLE plazo_parametro ADD CONSTRAINT fk_pp_plazo_categoria " +
                    "FOREIGN KEY (plazo_id, tipo_prenda_id) REFERENCES plazo_prenda(plazo_id, tipo_prenda_id)");
        }
    }

    private TipoPrenda persistTipoPrenda(int id, String tipo) {
        TipoPrenda tp = new TipoPrenda();
        tp.setId(id);
        tp.setTipo(tipo);
        return em.persistAndFlush(tp);
    }

    private Plazo persistPlazo(TipoPrenda... tipos) {
        Plazo plazo = new Plazo();
        plazo.setNombre("Semanal");
        plazo.setDiasPorPeriodo(7);
        plazo.setNumeroPeriodos(4);
        plazo.setActivo(true);
        Set<TipoPrenda> tiposPrenda = new LinkedHashSet<>();
        for (TipoPrenda tp : tipos) {
            tiposPrenda.add(tp);
        }
        plazo.setTiposPrenda(tiposPrenda);
        return em.persistAndFlush(plazo);
    }

    private void persistPlazoParametro(Long plazoId, Integer tipoPrendaId, Integer sucursalId) {
        PlazoParametro pp = new PlazoParametro();
        pp.setPlazoId(plazoId);
        pp.setTipoPrendaId(tipoPrendaId);
        pp.setSucursalId(sucursalId);
        pp.setCreadoEn(LocalDateTime.now());
        pp.setActualizadoEn(LocalDateTime.now());
        em.persistAndFlush(pp);
    }

    @Test
    void updatingPlazoWithSameTiposPrenda_doesNotThrow_whenPlazoParametroExists() {
        // Given: un Plazo con ALHAJA asociado, y un PlazoParametro ya configurado
        // para esa combinación (plazo_id, tipo_prenda_id) — el estado normal de
        // cualquier plazo real ya configurado con parámetros de préstamo.
        TipoPrenda alhaja = persistTipoPrenda(1, "ALHAJA");
        Plazo plazo = persistPlazo(alhaja);
        persistPlazoParametro(plazo.getId(), alhaja.getId(), 1);
        em.clear();

        // When: se replica exactamente lo que hace PlazoService.update() — cargar el
        // Plazo administrado y mutar tiposPrenda IN SITU (removeIf + addAll, nunca
        // reemplazar la referencia) con los MISMOS tipos de prenda (igual que reenviar
        // el formulario de edición sin tocar la selección de tipos de prenda).
        Plazo managed = plazoRepository.findById(plazo.getId()).orElseThrow();
        TipoPrenda alhajaReloaded = em.find(TipoPrenda.class, 1);
        Set<TipoPrenda> sameTipos = new LinkedHashSet<>();
        sameTipos.add(alhajaReloaded);
        managed.getTiposPrenda().removeIf(tp -> !sameTipos.contains(tp));
        managed.getTiposPrenda().addAll(sameTipos);

        // Then: no debe lanzar la violación de FK fk_pp_plazo_categoria
        assertThatCode(() -> {
            plazoRepository.save(managed);
            em.flush();
        }).doesNotThrowAnyException();
    }

    @Test
    void updatingPlazoWithSameTiposPrenda_preservesExistingPlazoParametro() {
        // Given
        TipoPrenda alhaja = persistTipoPrenda(1, "ALHAJA");
        Plazo plazo = persistPlazo(alhaja);
        persistPlazoParametro(plazo.getId(), alhaja.getId(), 1);
        em.clear();

        // When
        Plazo managed = plazoRepository.findById(plazo.getId()).orElseThrow();
        TipoPrenda alhajaReloaded = em.find(TipoPrenda.class, 1);
        Set<TipoPrenda> sameTipos = new LinkedHashSet<>();
        sameTipos.add(alhajaReloaded);
        managed.getTiposPrenda().removeIf(tp -> !sameTipos.contains(tp));
        managed.getTiposPrenda().addAll(sameTipos);
        plazoRepository.save(managed);
        em.flush();
        em.clear();

        // Then: el PlazoParametro configurado sigue intacto (no fue borrado en cascada)
        PlazoParametro found = em.getEntityManager()
                .createQuery("select pp from PlazoParametro pp where pp.plazoId = :pid", PlazoParametro.class)
                .setParameter("pid", plazo.getId())
                .getSingleResult();
        assertThat(found).isNotNull();
        assertThat(found.getTipoPrendaId()).isEqualTo(alhaja.getId());
    }
}
