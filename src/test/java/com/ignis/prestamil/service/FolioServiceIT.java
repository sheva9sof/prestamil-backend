package com.ignis.prestamil.service;

import com.ignis.prestamil.model.FolioSecuencia;
import com.ignis.prestamil.model.FolioSecuenciaId;
import com.ignis.prestamil.model.SerieFolio;
import com.ignis.prestamil.repository.FolioSecuenciaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contador genérico de folios (CAJ-01a) contra H2, con el upsert y el bloqueo de fila del repositorio.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(FolioService.class)
class FolioServiceIT {

    @Autowired FolioService folioService;
    @Autowired FolioSecuenciaRepository folioSecuenciaRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void consecutivosPorSucursalYSerie_independientesEntreSi() {
        assertThat(folioService.siguiente(1, SerieFolio.NOTA_VENTA)).isEqualTo(1L);
        assertThat(folioService.siguiente(1, SerieFolio.NOTA_VENTA)).isEqualTo(2L);
        // Otra serie de la misma sucursal lleva su propia numeración
        assertThat(folioService.siguiente(1, SerieFolio.TRASPASO_CAJA)).isEqualTo(1L);
        // Otra sucursal también
        assertThat(folioService.siguiente(2, SerieFolio.NOTA_VENTA)).isEqualTo(1L);
        assertThat(folioService.siguiente(1, SerieFolio.NOTA_VENTA)).isEqualTo(3L);
        assertThat(folioService.siguiente(1, SerieFolio.TRASPASO_CAJA)).isEqualTo(2L);
    }

    @Test
    void primeraLlamadaSinFila_creaElContadorYEmiteElUno() {
        FolioSecuenciaId id = new FolioSecuenciaId(3, SerieFolio.NOTA_VENTA);
        assertThat(folioSecuenciaRepository.findById(id)).isEmpty();

        assertThat(folioService.siguiente(3, SerieFolio.NOTA_VENTA)).isEqualTo(1L);

        assertThat(folioSecuenciaRepository.findById(id))
                .get().extracting(FolioSecuencia::getUltimoFolio).isEqualTo(1L);
    }

    @Test
    void continuaDesdeElUltimoFolioDeLaSerie() {
        // Si Sistemas ajusta el contador para seguir una numeración externa
        FolioSecuencia existente = new FolioSecuencia();
        existente.setSucursalId(4);
        existente.setSerie(SerieFolio.TRASPASO_CAJA);
        existente.setUltimoFolio(1520L);
        folioSecuenciaRepository.saveAndFlush(existente);

        assertThat(folioService.siguiente(4, SerieFolio.TRASPASO_CAJA)).isEqualTo(1521L);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void sinTransaccionDelLlamador_rechazaYNoConsumeFolio() {
        assertThatThrownBy(() -> folioService.siguiente(6, SerieFolio.NOTA_VENTA))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(folioSecuenciaRepository.findById(new FolioSecuenciaId(6, SerieFolio.NOTA_VENTA))).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void operacionQueFalla_noConsumeElFolio() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            tx.executeWithoutResult(s -> folioService.siguiente(7, SerieFolio.NOTA_VENTA));
            assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
                folioService.siguiente(7, SerieFolio.NOTA_VENTA);
                throw new IllegalStateException("falla la operación que usaba el folio");
            })).isInstanceOf(IllegalStateException.class);

            // El 2 se revirtió con la operación: el siguiente folio vuelve a ser 2, sin hueco
            Long siguiente = tx.execute(s -> folioService.siguiente(7, SerieFolio.NOTA_VENTA));
            assertThat(siguiente).isEqualTo(2L);
        } finally {
            folioSecuenciaRepository.deleteById(new FolioSecuenciaId(7, SerieFolio.NOTA_VENTA));
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void dosHilosConcurrentes_noRepitenFolio() throws Exception {
        // Sin fila previa: los dos hilos compiten también por crear el contador
        int porHilo = 25;
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch salida = new CountDownLatch(1);
        Callable<List<Long>> tarea = () -> {
            salida.await();
            List<Long> folios = new ArrayList<>();
            for (int i = 0; i < porHilo; i++) {
                folios.add(tx.execute(s -> folioService.siguiente(8, SerieFolio.NOTA_VENTA)));
            }
            return folios;
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<List<Long>> hilo1 = pool.submit(tarea);
            Future<List<Long>> hilo2 = pool.submit(tarea);
            salida.countDown();

            List<Long> todos = new ArrayList<>(hilo1.get(30, TimeUnit.SECONDS));
            todos.addAll(hilo2.get(30, TimeUnit.SECONDS));

            assertThat(todos).hasSize(2 * porHilo).doesNotHaveDuplicates();
            // Sin huecos: se emitieron exactamente 1..50
            assertThat(todos).containsExactlyInAnyOrderElementsOf(
                    LongStream.rangeClosed(1, 2L * porHilo).boxed().toList());
            assertThat(folioSecuenciaRepository.findById(new FolioSecuenciaId(8, SerieFolio.NOTA_VENTA)))
                    .get().extracting(FolioSecuencia::getUltimoFolio).isEqualTo(2L * porHilo);
        } finally {
            pool.shutdownNow();
            folioSecuenciaRepository.deleteById(new FolioSecuenciaId(8, SerieFolio.NOTA_VENTA));
        }
    }
}
