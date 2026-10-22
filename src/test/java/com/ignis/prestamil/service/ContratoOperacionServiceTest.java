package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.mapper.ContratoMapper;
import com.ignis.prestamil.model.AccionContrato;
import com.ignis.prestamil.model.BuscarContratoPor;
import com.ignis.prestamil.model.Cliente;
import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.EstatusContrato;
import com.ignis.prestamil.model.EstatusOperativo;
import com.ignis.prestamil.model.FiltroEstatusOperacion;
import com.ignis.prestamil.model.MovimientoContrato;
import com.ignis.prestamil.model.PartidaContrato;
import com.ignis.prestamil.model.Plazo;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.model.TipoMovimiento;
import com.ignis.prestamil.model.TipoPrenda;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.ContratoRepository;
import com.ignis.prestamil.repository.MovimientoContratoRepository;
import com.ignis.prestamil.repository.PlazoParametroRepository;
import com.ignis.prestamil.response.ContratoOperacionDetalleResponse;
import com.ignis.prestamil.response.ContratoOperacionResponse;
import com.ignis.prestamil.response.PageResponse;
import com.ignis.prestamil.service.calculo.CalculoContratoService;
import com.ignis.prestamil.service.calculo.ParametrosSistemaCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests del listado y detalle de la pantalla de Finiquitos y Refrendos (F2). El motor y el resolver son
 * reales: se verifica que el servicio los consume, no se re-prueban sus reglas.
 */
@ExtendWith(MockitoExtension.class)
class ContratoOperacionServiceTest {

    @Mock ContratoRepository contratoRepository;
    @Mock PlazoParametroRepository plazoParametroRepository;
    @Mock MovimientoContratoRepository movimientoRepository;
    @Mock SucursalService sucursalService;
    @Mock ParametrosSistemaCache parametrosSistemaCache;

    ContratoOperacionService service;
    final LocalDate hoy = LocalDate.now();

    @BeforeEach
    void setUp() {
        service = new ContratoOperacionService(contratoRepository, plazoParametroRepository, movimientoRepository,
                sucursalService, new CalculoContratoService(parametrosSistemaCache), new ContratoMapper());

        Sucursal sucursal = new Sucursal();
        sucursal.setId(1);
        lenient().when(sucursalService.findUnique()).thenReturn(Optional.of(sucursal));
        // Los contratos traen snapshot completo: el parámetro vigente no hace falta
        lenient().when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(anyLong(), anyInt(), anyInt()))
                .thenReturn(Optional.empty());
    }

    // =========================================================================
    // Fixtures
    // =========================================================================

    /**
     * Contrato semanal de 4 periodos como el 1493 de COCAE (interés 1.13% + almacenaje 0.60%), con
     * snapshot completo. El vencimiento queda {@code diasAtraso} días antes de hoy.
     */
    private Contrato contrato(long id, int diasAtraso, int diasGracia) {
        Contrato c = new Contrato();
        c.setId(id);
        c.setFolio(String.format("CTR-%06d", id));
        c.setSucursalId(1);
        c.setEstatus(EstatusContrato.VIGENTE);
        c.setNumRefrendos(0);
        c.setMontoPrestamo(new BigDecimal("1195.00"));
        c.setSaldoCapital(new BigDecimal("1195.00"));
        c.setMontoAvaluo(new BigDecimal("1600.00"));
        c.setFechaApertura(LocalDateTime.now().minusDays(28L + diasAtraso));
        c.setFechaVencimiento(hoy.minusDays(diasAtraso));
        c.setFechaContrato(c.getFechaVencimiento().minusDays(28));
        c.setFechaComercializacion(c.getFechaVencimiento().plusDays(15));

        Plazo plazo = new Plazo();
        plazo.setId(1L);
        plazo.setNombre("Semanal");
        plazo.setDiasPorPeriodo(7);
        plazo.setNumeroPeriodos(4);
        c.setPlazo(plazo);

        Cliente cliente = new Cliente();
        cliente.setId(7);
        cliente.setNombre("Juan");
        cliente.setApellidoPaterno("Pérez");
        cliente.setApellidoMaterno("López");
        cliente.setTelefono("7771234567");
        c.setCliente(cliente);

        TipoPrenda alhaja = new TipoPrenda();
        alhaja.setId(1);
        alhaja.setTipo("ALHAJA");
        PartidaContrato partida = new PartidaContrato();
        partida.setId(id * 10);
        partida.setNumPartida(1);
        partida.setTipoPrenda(alhaja);
        partida.setDescripcion("Anillo");
        partida.setMontoPrestamo(c.getMontoPrestamo());
        partida.setAvaluoReal(c.getMontoAvaluo());
        partida.setAvaluoContrato(c.getMontoAvaluo());
        partida.setContrato(c);
        c.setPartidas(new java.util.ArrayList<>(List.of(partida)));

        c.setSnapPorcInteres(new BigDecimal("1.13"));
        c.setSnapPorcAlmacen(new BigDecimal("0.60"));
        c.setSnapPorcGastosAdmin(new BigDecimal("18.00"));
        c.setSnapPorcSancionSemanal(new BigDecimal("2.00"));
        c.setSnapDiasGraciaSancion(diasGracia);
        c.setSnapAplicarSancionPeriodo(true);
        c.setSnapIvaPorcentaje(new BigDecimal("16.00"));
        return c;
    }

    @SuppressWarnings("unchecked")
    private void listaDeVencidos(Contrato... contratos) {
        when(contratoRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(contratos));
    }

    private static List<Long> ids(PageResponse<ContratoOperacionResponse> pagina) {
        return pagina.getContent().stream().map(ContratoOperacionResponse::getId).toList();
    }

    // =========================================================================
    // Listado
    // =========================================================================

    @Nested
    class GraciaYVencidos {

        // A: 1 día tarde con 2 de gracia; B: 1 día tarde sin gracia; C: 5 días tarde con 2 de gracia
        Contrato enGracia;
        Contrato sinGracia;
        Contrato vencido;

        @BeforeEach
        void contratos() {
            enGracia = contrato(1, 1, 2);
            sinGracia = contrato(2, 1, 0);
            vencido = contrato(3, 5, 2);
        }

        @Test
        void periodoDeGraciaUsaLosDiasDeGraciaDeCadaContrato() {
            listaDeVencidos(enGracia, sinGracia, vencido);

            PageResponse<ContratoOperacionResponse> r =
                    service.buscar(null, BuscarContratoPor.CONTRATO, null, FiltroEstatusOperacion.PERIODO_GRACIA, 0, 20);

            assertThat(ids(r)).containsExactly(1L);
            assertThat(r.getTotalElements()).isEqualTo(1);
            assertThat(r.getContent().get(0).getEstatus()).isEqualTo(EstatusOperativo.EN_GRACIA);
        }

        @Test
        void vencidosSonLosQueRebasaronSuGracia() {
            listaDeVencidos(enGracia, sinGracia, vencido);

            PageResponse<ContratoOperacionResponse> r =
                    service.buscar(null, BuscarContratoPor.CONTRATO, null, FiltroEstatusOperacion.VENCIDOS, 0, 20);

            assertThat(ids(r)).containsExactly(2L, 3L);
            assertThat(r.getContent()).allSatisfy(f -> {
                assertThat(f.getEstatus()).isEqualTo(EstatusOperativo.VENCIDO);
                assertThat(f.getAccionesDisponibles()).contains(
                        AccionContrato.REFRENDO_EXTEMPORANEO, AccionContrato.FINIQUITO_EXTEMPORANEO)
                        .doesNotContain(AccionContrato.REFRENDO, AccionContrato.FINIQUITO, AccionContrato.ABONO_CAPITAL);
            });
        }

        @Test
        void paginaEnMemoriaTrasFiltrar() {
            listaDeVencidos(enGracia, sinGracia, vencido);

            PageResponse<ContratoOperacionResponse> r =
                    service.buscar(null, BuscarContratoPor.CONTRATO, null, FiltroEstatusOperacion.VENCIDOS, 1, 1);

            assertThat(ids(r)).containsExactly(3L);
            assertThat(r.getPage()).isEqualTo(1);
            assertThat(r.getTotalElements()).isEqualTo(2);
            assertThat(r.getTotalPages()).isEqualTo(2);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void filaConEstatusAccionesEInteresPorPeriodo() {
        // 1493: vigente con 26 días transcurridos -> 4 periodos; interés por periodo 1,195 x 1.73% = 20.67
        Contrato c = contrato(1493, -2, 2);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(contratoRepository.findAll(any(Specification.class), pageable.capture()))
                .thenAnswer(inv -> new PageImpl<>(List.of(c), inv.getArgument(1), 1));

        PageResponse<ContratoOperacionResponse> r =
                service.buscar(null, BuscarContratoPor.CONTRATO, null, FiltroEstatusOperacion.TODOS, 0, 500);

        // El tamaño de página se limita a 100
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        ContratoOperacionResponse f = r.getContent().get(0);
        assertThat(f.getEstatus()).isEqualTo(EstatusOperativo.VIGENTE);
        assertThat(f.getInteresPorPeriodo()).isEqualByComparingTo("20.6735");
        assertThat(f.getDiasGracia()).isEqualTo(2);
        assertThat(f.getRamo()).isEqualTo("ALHAJA");
        assertThat(f.getNumPartidas()).isEqualTo(1);
        assertThat(f.getNombreCliente()).isEqualTo("Juan Pérez López");
        assertThat(f.getNumeroPeriodos()).isEqualTo(4);
        assertThat(f.getDiasPorPeriodo()).isEqualTo(7);
        assertThat(f.getAccionesDisponibles()).containsExactlyInAnyOrder(
                AccionContrato.REFRENDO, AccionContrato.FINIQUITO, AccionContrato.ABONO_CAPITAL,
                AccionContrato.REFRENDO_PARCIAL, AccionContrato.REPOSICION,
                AccionContrato.CONSULTA, AccionContrato.CANCELACION);
    }

    @Test
    void numeroDeClienteNoNumericoEs400() {
        assertThatThrownBy(() -> service.buscar(
                "Juan", BuscarContratoPor.NUM_CLIENTE, null, FiltroEstatusOperacion.TODOS, 0, 20))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("numérico");
        verifyNoInteractions(contratoRepository);
    }

    @Test
    void fechaDeContratoInvalidaEs400() {
        assertThatThrownBy(() -> service.buscar(
                "31/02/2026", BuscarContratoPor.FECHA_CONTRATO, null, FiltroEstatusOperacion.TODOS, 0, 20))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("dd/mm/aaaa");
        verifyNoInteractions(contratoRepository);
    }

    // =========================================================================
    // Detalle
    // =========================================================================

    @Nested
    class Detalle {

        @Test
        void vigenteConPeriodosPartidasYUltimoMovimiento() {
            Contrato c = contrato(1493, -2, 2);
            when(contratoRepository.findById(1493L)).thenReturn(Optional.of(c));
            MovimientoContrato emp = new MovimientoContrato();
            emp.setId(99L);
            emp.setTipo(TipoMovimiento.EMP);
            emp.setMonto(c.getMontoPrestamo());
            emp.setFecha(c.getFechaApertura());
            Usuario cajero = new Usuario();
            cajero.setNombreUsuario("cajero1");
            emp.setUsuario(cajero);
            when(movimientoRepository.findFirstByContratoIdAndCanceladoFalseOrderByFechaDescIdDesc(1493L))
                    .thenReturn(Optional.of(emp));

            ContratoOperacionDetalleResponse r = service.detalle(1493L);

            assertThat(r.getEstatus()).isEqualTo(EstatusOperativo.VIGENTE);
            assertThat(r.getPeriodosTranscurridos()).isEqualTo(4);
            assertThat(r.getPeriodosExtemporaneos()).isZero();
            assertThat(r.getDiasAtraso()).isZero();
            assertThat(r.getTelefonoCliente()).isEqualTo("7771234567");
            assertThat(r.getPartidas()).hasSize(1);
            assertThat(r.getPartidas().get(0).getDescripcion()).isEqualTo("Anillo");
            assertThat(r.getUltimoMovimiento().getTipo()).isEqualTo(TipoMovimiento.EMP);
            assertThat(r.getUltimoMovimiento().getNombreUsuario()).isEqualTo("cajero1");
        }

        @Test
        void finiquitadoSinPeriodosYSoloConsulta() {
            Contrato c = contrato(10, 30, 2);
            c.setEstatus(EstatusContrato.FINIQUITADO);
            when(contratoRepository.findById(10L)).thenReturn(Optional.of(c));

            ContratoOperacionDetalleResponse r = service.detalle(10L);

            assertThat(r.getEstatus()).isEqualTo(EstatusOperativo.FINIQUITADO);
            assertThat(r.getPeriodosTranscurridos()).isNull();
            assertThat(r.getUltimoMovimiento()).isNull();
            assertThat(r.getAccionesDisponibles())
                    .containsExactlyInAnyOrder(AccionContrato.CONSULTA, AccionContrato.CANCELACION);
        }

        @Test
        void contratoDeOtraSucursalEs404() {
            Contrato c = contrato(11, 0, 2);
            c.setSucursalId(2);
            when(contratoRepository.findById(11L)).thenReturn(Optional.of(c));

            assertThatThrownBy(() -> service.detalle(11L)).isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
