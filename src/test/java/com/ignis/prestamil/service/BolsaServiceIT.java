package com.ignis.prestamil.service;

import com.ignis.prestamil.model.FolioBolsa;
import com.ignis.prestamil.model.FolioCodigoBarras;
import com.ignis.prestamil.model.PartidaContrato;
import com.ignis.prestamil.repository.FolioBolsaRepository;
import com.ignis.prestamil.repository.FolioCodigoBarrasRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contadores reales de bolsa y código de barras (D-10) contra H2, con el bloqueo de fila de los
 * repositorios.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(BolsaService.class)
class BolsaServiceIT {

    @Autowired BolsaService bolsaService;
    @Autowired FolioBolsaRepository folioBolsaRepository;
    @Autowired FolioCodigoBarrasRepository folioCodigoBarrasRepository;

    @Test
    void bolsa_consecutivaPorSucursal_eIndependienteEntreSucursales() {
        assertThat(bolsaService.siguienteNumBolsa(1)).isEqualTo(1);
        assertThat(bolsaService.siguienteNumBolsa(1)).isEqualTo(2);
        // Otra sucursal lleva su propia numeración
        assertThat(bolsaService.siguienteNumBolsa(2)).isEqualTo(1);
        assertThat(bolsaService.siguienteNumBolsa(1)).isEqualTo(3);
    }

    @Test
    void bolsa_continuaDesdeElUltimoFolioDeLaSucursal() {
        // Como tras el backfill del changeset 043, o si Sistemas ajusta el contador para seguir a COCAE
        FolioBolsa existente = new FolioBolsa();
        existente.setSucursalId(5);
        existente.setUltimoFolio(3968);
        folioBolsaRepository.save(existente);

        assertThat(bolsaService.siguienteNumBolsa(5)).isEqualTo(3969);
    }

    @Test
    void codigosDeBarras_unicosEntreContratosYSucursales_yNuncaReutilizados() {
        List<PartidaContrato> sucursal1 = partidas(3);
        List<PartidaContrato> sucursal2 = partidas(2);
        List<PartidaContrato> otraVezSucursal1 = partidas(1);

        bolsaService.asignarCodigosBarras(sucursal1);
        bolsaService.asignarCodigosBarras(sucursal2);
        bolsaService.asignarCodigosBarras(otraVezSucursal1);

        List<String> todos = new ArrayList<>();
        sucursal1.forEach(p -> todos.add(p.getCodigoBarras()));
        sucursal2.forEach(p -> todos.add(p.getCodigoBarras()));
        otraVezSucursal1.forEach(p -> todos.add(p.getCodigoBarras()));

        assertThat(todos).doesNotHaveDuplicates().doesNotContainNull();
        assertThat(todos).containsExactly(
                "0000000001", "0000000002", "0000000003", "0000000004", "0000000005", "0000000006");
        // El contador quedó en el último emitido: aunque se borren partidas, no se vuelve a emitir un código
        assertThat(folioCodigoBarrasRepository.findById(FolioCodigoBarras.ID_UNICO))
                .get().extracting(FolioCodigoBarras::getUltimoFolio).isEqualTo(6L);
    }

    @Test
    void codigosDeBarras_contratoSinPartidas_noMueveElContador() {
        bolsaService.asignarCodigosBarras(new ArrayList<>());

        assertThat(folioCodigoBarrasRepository.findById(FolioCodigoBarras.ID_UNICO)).isEmpty();
    }

    private static List<PartidaContrato> partidas(int n) {
        List<PartidaContrato> lista = new ArrayList<>();
        IntStream.rangeClosed(1, n).forEach(i -> {
            PartidaContrato p = new PartidaContrato();
            p.setNumPartida(i);
            lista.add(p);
        });
        return lista;
    }
}
