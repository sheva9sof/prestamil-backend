package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.model.Banco;
import com.ignis.prestamil.repository.BancoRepository;
import com.ignis.prestamil.request.BancoRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BancoServiceTest {

    @Mock BancoRepository repository;

    BancoService service;

    @BeforeEach
    void setUp() {
        service = new BancoService(repository);
        lenient().when(repository.save(any(Banco.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static BancoRequest request(String nombre, Boolean activo) {
        BancoRequest r = new BancoRequest();
        r.setNombre(nombre);
        r.setActivo(activo);
        return r;
    }

    private static Banco banco(int id, String nombre, boolean activo) {
        Banco b = new Banco();
        b.setId(id);
        b.setNombre(nombre);
        b.setActivo(activo);
        return b;
    }

    @Test
    void listar_soloActivosParaLaVentanaDeCobro() {
        List<Banco> activos = List.of(banco(1, "BBVA", true));
        when(repository.findByActivoTrueOrderByNombreAsc()).thenReturn(activos);

        assertThat(service.listar(true)).isEqualTo(activos);
        verify(repository, never()).findAllByOrderByNombreAsc();
    }

    @Test
    void crear_recortaElNombreYQuedaActivoPorDefecto() {
        when(repository.existsByNombreIgnoreCase("Banco del Bajío")).thenReturn(false);

        Banco creado = service.crear(request("  Banco del Bajío ", null));

        assertThat(creado.getNombre()).isEqualTo("Banco del Bajío");
        assertThat(creado.getActivo()).isTrue();
    }

    @Test
    void crear_nombreRepetido_400() {
        when(repository.existsByNombreIgnoreCase("bbva")).thenReturn(true);

        assertThatThrownBy(() -> service.crear(request("bbva", true)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Ya existe");
        verify(repository, never()).save(any());
    }

    @Test
    void actualizar_desactivaSinBorrar() {
        Banco bbva = banco(1, "BBVA", true);
        when(repository.findById(1)).thenReturn(Optional.of(bbva));
        when(repository.existsByNombreIgnoreCaseAndIdNot("BBVA", 1)).thenReturn(false);

        Banco actualizado = service.actualizar(1, request("BBVA", false));

        assertThat(actualizado.getActivo()).isFalse();
        verify(repository, never()).delete(any());
    }

    @Test
    void actualizar_conNombreDeOtroBanco_400() {
        when(repository.findById(1)).thenReturn(Optional.of(banco(1, "BBVA", true)));
        when(repository.existsByNombreIgnoreCaseAndIdNot("Santander", 1)).thenReturn(true);

        assertThatThrownBy(() -> service.actualizar(1, request("Santander", true)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void actualizar_inexistente_404() {
        when(repository.findById(9)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizar(9, request("X", true)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
