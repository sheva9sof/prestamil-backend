package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.model.CatSubtipoPrenda;
import com.ignis.prestamil.model.CatValorPrenda;
import com.ignis.prestamil.model.TipoPrenda;
import com.ignis.prestamil.repository.CatSubtipoPrendaRepository;
import com.ignis.prestamil.repository.CatValorPrendaRepository;
import com.ignis.prestamil.repository.PartidaContratoRepository;
import com.ignis.prestamil.request.CatValorPrendaRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatValorPrendaServiceTest {

    @Mock
    CatValorPrendaRepository repository;

    @Mock
    CatSubtipoPrendaRepository subtipoRepository;

    @Mock
    PartidaContratoRepository partidaContratoRepository;

    CatValorPrendaService service;

    @BeforeEach
    void setUp() {
        service = new CatValorPrendaService(repository, subtipoRepository, partidaContratoRepository);
    }

    @Test
    void create_guardaValorEnCategoriaDelTipoSeleccionado() {
        CatSubtipoPrenda subtipo = buildSubtipo(6, 3);
        CatValorPrendaRequest request = buildRequest(3, 6);
        request.setDescripcion("  Celular  ");
        request.setClave("  esclava-14  ");
        request.setContienePiedad(null);

        when(subtipoRepository.findById(6)).thenReturn(Optional.of(subtipo));
        when(repository.save(any(CatValorPrenda.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CatValorPrenda saved = service.create(request);

        assertThat(saved.getSubtipoPrenda()).isSameAs(subtipo);
        assertThat(saved.getDescripcion()).isEqualTo("Celular");
        assertThat(saved.getClave()).isEqualTo("esclava-14");
        assertThat(saved.getContienePiedad()).isFalse();
        verify(repository).save(saved);
    }

    @Test
    void findAllOrdered_devuelveCatalogoCompletoOrdenadoDelRepositorio() {
        CatValorPrenda primero = new CatValorPrenda();
        primero.setIdValorAtributo(2);
        CatValorPrenda segundo = new CatValorPrenda();
        segundo.setIdValorAtributo(5);
        when(repository.findAllByOrderByIdValorAtributoAsc())
                .thenReturn(java.util.List.of(primero, segundo));

        assertThat(service.findAllOrdered()).containsExactly(primero, segundo);
    }

    @Test
    void create_rechazaCategoriaDeOtroTipoDePrenda() {
        CatValorPrendaRequest request = buildRequest(1, 6);
        when(subtipoRepository.findById(6)).thenReturn(Optional.of(buildSubtipo(6, 3)));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.create(request));

        assertThat(error.getMessage()).contains("no pertenece al tipo de prenda");
        verify(repository, never()).save(any());
    }

    @Test
    void update_conservaCategoriaYActualizaCamposEditables() {
        CatSubtipoPrenda subtipo = buildSubtipo(4, 1);
        CatValorPrenda valor = new CatValorPrenda();
        valor.setIdValorAtributo(12);
        valor.setSubtipoPrenda(subtipo);
        valor.setDescripcion("Anterior");

        CatValorPrendaRequest request = buildRequest(1, 4);
        request.setDescripcion("Anillo 14K");
        request.setKilataje(14);
        request.setContienePiedad(true);

        when(repository.findById(12)).thenReturn(Optional.of(valor));
        when(subtipoRepository.findById(4)).thenReturn(Optional.of(subtipo));
        when(repository.save(valor)).thenReturn(valor);

        CatValorPrenda updated = service.update(12, request);

        assertThat(updated.getDescripcion()).isEqualTo("Anillo 14K");
        assertThat(updated.getKilataje()).isEqualTo(14);
        assertThat(updated.getContienePiedad()).isTrue();
        assertThat(updated.getSubtipoPrenda()).isSameAs(subtipo);
    }

    @Test
    void deleteValor_borraFisicamenteCuandoNoEstaUsadaEnContratos() {
        CatValorPrenda valor = new CatValorPrenda();
        valor.setIdValorAtributo(30);

        when(repository.findById(30)).thenReturn(Optional.of(valor));
        when(partidaContratoRepository.countByValorPrendaIdValorAtributo(30)).thenReturn(0L);

        service.deleteValor(30);

        verify(repository).delete(valor);
    }

    @Test
    void deleteValor_rechazaCuandoLaPrendaYaSeUsoEnUnContrato() {
        CatValorPrenda valor = new CatValorPrenda();
        valor.setIdValorAtributo(31);

        when(repository.findById(31)).thenReturn(Optional.of(valor));
        when(partidaContratoRepository.countByValorPrendaIdValorAtributo(31)).thenReturn(3L);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.deleteValor(31));

        assertThat(ex.getMessage()).contains("3 partidas de contrato");
        verify(repository, never()).delete(any(CatValorPrenda.class));
    }

    private CatValorPrendaRequest buildRequest(int tipoId, int atributoId) {
        CatValorPrendaRequest request = new CatValorPrendaRequest();
        request.setIdTipoPrenda(tipoId);
        request.setIdAtributo(atributoId);
        request.setDescripcion("Valor");
        request.setContienePiedad(false);
        return request;
    }

    private CatSubtipoPrenda buildSubtipo(int atributoId, int tipoId) {
        TipoPrenda tipo = new TipoPrenda();
        tipo.setId(tipoId);
        CatSubtipoPrenda subtipo = new CatSubtipoPrenda();
        subtipo.setIdAtributo(atributoId);
        subtipo.setTipoPrenda(tipo);
        return subtipo;
    }
}
