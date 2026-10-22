package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.exception.ForbiddenException;
import com.ignis.prestamil.model.CatSubtipoPrenda;
import com.ignis.prestamil.model.CatValorPrenda;
import com.ignis.prestamil.model.Configuracion;
import com.ignis.prestamil.model.Rol;
import com.ignis.prestamil.model.TipoPrenda;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.CatSubtipoPrendaRepository;
import com.ignis.prestamil.repository.CatValorPrendaRepository;
import com.ignis.prestamil.repository.ConfiguracionRepository;
import com.ignis.prestamil.repository.PartidaContratoRepository;
import com.ignis.prestamil.repository.UsuarioRepository;
import com.ignis.prestamil.request.CatValorPrendaRequest;
import com.ignis.prestamil.util.Constantes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
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

    @Mock
    UsuarioRepository usuarioRepository;

    @Mock
    ConfiguracionRepository configuracionRepository;

    CatValorPrendaService service;

    @BeforeEach
    void setUp() {
        service = new CatValorPrendaService(repository, subtipoRepository, partidaContratoRepository,
                usuarioRepository, configuracionRepository);

        // "sistemas" (rol 1) administra el catálogo; "gerente" (rol 5) de sucursal solo consulta.
        lenient().when(usuarioRepository.findByNombreUsuario("sistemas")).thenReturn(Optional.of(usuarioConRol(1)));
        lenient().when(usuarioRepository.findByNombreUsuario("gerente")).thenReturn(Optional.of(usuarioConRol(5)));
        Configuracion roles = new Configuracion();
        roles.setValorCadena("1");
        lenient().when(configuracionRepository.findByConfiguracion(Constantes.ROLES_PERMITIDOS_CATALOGO_PRENDAS))
                .thenReturn(Optional.of(roles));
    }

    @Test
    void create_guardaValorEnCategoriaDelTipoSeleccionado() {
        CatSubtipoPrenda subtipo = buildSubtipo(6, 3);
        CatValorPrendaRequest request = buildRequest(3, 6);
        request.setDescripcion("  Celular  ");
        request.setClave("  esclava-14  ");
        request.setContienePiedra(null);

        when(subtipoRepository.findById(6)).thenReturn(Optional.of(subtipo));
        when(repository.save(any(CatValorPrenda.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CatValorPrenda saved = service.create(request, "sistemas");

        assertThat(saved.getSubtipoPrenda()).isSameAs(subtipo);
        assertThat(saved.getDescripcion()).isEqualTo("Celular");
        assertThat(saved.getClave()).isEqualTo("esclava-14");
        assertThat(saved.getContienePiedra()).isFalse();
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

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.create(request, "sistemas"));

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

        valor.setPendienteRevision(true);

        CatValorPrendaRequest request = buildRequest(1, 4);
        request.setDescripcion("Anillo 14K");
        request.setKilataje(14);
        request.setContienePiedra(true);

        when(repository.findById(12)).thenReturn(Optional.of(valor));
        when(subtipoRepository.findById(4)).thenReturn(Optional.of(subtipo));
        when(repository.save(valor)).thenReturn(valor);

        CatValorPrenda updated = service.update(12, request, "sistemas");

        assertThat(updated.getDescripcion()).isEqualTo("Anillo 14K");
        assertThat(updated.getKilataje()).isEqualTo(14);
        assertThat(updated.getContienePiedra()).isTrue();
        assertThat(updated.getSubtipoPrenda()).isSameAs(subtipo);
        // Sistemas ya capturó datos definitivos: el registro sale de la lista de pendientes (changeset 041)
        assertThat(updated.getPendienteRevision()).isFalse();
    }

    @Test
    void create_sinClave_rechaza() {
        CatValorPrendaRequest request = buildRequest(1, 4);
        request.setClave("  ");
        when(subtipoRepository.findById(4)).thenReturn(Optional.of(buildSubtipo(4, 1)));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.create(request, "sistemas"));

        assertThat(ex.getMessage()).contains("clave");
        verify(repository, never()).save(any());
    }

    @Test
    void create_sinDescripcionGeneral_rechaza() {
        CatValorPrendaRequest request = buildRequest(1, 4);
        request.setDescripcion("   ");
        when(subtipoRepository.findById(4)).thenReturn(Optional.of(buildSubtipo(4, 1)));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.create(request, "sistemas"));

        assertThat(ex.getMessage()).contains("descripción general");
        verify(repository, never()).save(any());
    }

    @Test
    void create_update_delete_rolDeSucursal_403SinTocarElCatalogo() {
        CatValorPrendaRequest request = buildRequest(1, 4);

        assertThrows(ForbiddenException.class, () -> service.create(request, "gerente"));
        assertThrows(ForbiddenException.class, () -> service.update(12, request, "gerente"));
        assertThrows(ForbiddenException.class, () -> service.deleteValor(12, "gerente"));

        verify(repository, never()).save(any());
        verify(repository, never()).delete(any(CatValorPrenda.class));
    }

    @Test
    void deleteValor_borraFisicamenteCuandoNoEstaUsadaEnContratos() {
        CatValorPrenda valor = new CatValorPrenda();
        valor.setIdValorAtributo(30);

        when(repository.findById(30)).thenReturn(Optional.of(valor));
        when(partidaContratoRepository.countByValorPrendaIdValorAtributo(30)).thenReturn(0L);

        service.deleteValor(30, "sistemas");

        verify(repository).delete(valor);
    }

    @Test
    void deleteValor_rechazaCuandoLaPrendaYaSeUsoEnUnContrato() {
        CatValorPrenda valor = new CatValorPrenda();
        valor.setIdValorAtributo(31);

        when(repository.findById(31)).thenReturn(Optional.of(valor));
        when(partidaContratoRepository.countByValorPrendaIdValorAtributo(31)).thenReturn(3L);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.deleteValor(31, "sistemas"));

        assertThat(ex.getMessage()).contains("3 partidas de contrato");
        verify(repository, never()).delete(any(CatValorPrenda.class));
    }

    private CatValorPrendaRequest buildRequest(int tipoId, int atributoId) {
        CatValorPrendaRequest request = new CatValorPrendaRequest();
        request.setIdTipoPrenda(tipoId);
        request.setIdAtributo(atributoId);
        request.setDescripcion("Valor");
        request.setClave("CLV" + atributoId);
        request.setContienePiedra(false);
        return request;
    }

    private Usuario usuarioConRol(int rolId) {
        Rol rol = new Rol();
        rol.setId(rolId);
        Usuario usuario = new Usuario();
        usuario.setRol(rol);
        return usuario;
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
