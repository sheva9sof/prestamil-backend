package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.mapper.ContratoMapper;
import com.ignis.prestamil.model.Cliente;
import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.Plazo;
import com.ignis.prestamil.model.PlazoHechuraAlhaja;
import com.ignis.prestamil.model.PlazoHechuraAlhajaId;
import com.ignis.prestamil.model.TipoPrenda;
import com.ignis.prestamil.model.Turno;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.CatValorPrendaRepository;
import com.ignis.prestamil.repository.ClienteRepository;
import com.ignis.prestamil.repository.ContratoRepository;
import com.ignis.prestamil.repository.PlazoHechuraAlhajaRepository;
import com.ignis.prestamil.repository.PlazoParametroRepository;
import com.ignis.prestamil.repository.PlazoRepository;
import com.ignis.prestamil.repository.TipoPrendaRepository;
import com.ignis.prestamil.repository.TurnoRepository;
import com.ignis.prestamil.repository.UsuarioRepository;
import com.ignis.prestamil.request.ContratoRequest;
import com.ignis.prestamil.request.PartidaContratoRequest;
import com.ignis.prestamil.response.ContratoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Pruebas de regresión de la brecha de confianza servidor/cliente para partidas ALHAJA
 * (D-07, Pitfall 1 de PITFALLS.md) y de los rechazos de kilataje D-04/D-05.
 */
@ExtendWith(MockitoExtension.class)
class ContratoServiceTest {

    @Mock
    ContratoRepository repository;

    @Mock
    ClienteRepository clienteRepository;

    @Mock
    PlazoRepository plazoRepository;

    @Mock
    TurnoRepository turnoRepository;

    @Mock
    UsuarioRepository usuarioRepository;

    @Mock
    TipoPrendaRepository tipoPrendaRepository;

    @Mock
    CatValorPrendaRepository catValorPrendaRepository;

    @Mock
    ContratoMapper contratoMapper;

    @Mock
    PlazoParametroRepository plazoParametroRepository;

    @Mock
    PlazoService plazoService;

    @Mock
    PlazoHechuraAlhajaRepository plazoHechuraAlhajaRepository;

    ContratoService contratoService;

    @BeforeEach
    void setUp() {
        contratoService = new ContratoService(
                repository,
                clienteRepository,
                plazoRepository,
                turnoRepository,
                usuarioRepository,
                tipoPrendaRepository,
                catValorPrendaRepository,
                contratoMapper,
                plazoParametroRepository,
                plazoService,
                plazoHechuraAlhajaRepository
        );

        // Stubs comunes a todos los tests: turno activo, cajero, cliente, plazo y tipo de prenda ALHAJA
        Turno turno = new Turno();
        turno.setId(1);
        when(turnoRepository.findByActivo(true)).thenReturn(Optional.of(turno));

        Usuario usuario = new Usuario();
        usuario.setNombreUsuario("cajero1");
        when(usuarioRepository.findByNombreUsuario("cajero1")).thenReturn(Optional.of(usuario));

        Cliente cliente = new Cliente();
        cliente.setId(1);
        when(clienteRepository.findById(1)).thenReturn(Optional.of(cliente));

        Plazo plazo = new Plazo();
        plazo.setId(1L);
        plazo.setDiasPorPeriodo(7);
        plazo.setNumeroPeriodos(10);
        when(plazoRepository.findById(1L)).thenReturn(Optional.of(plazo));

        TipoPrenda alhaja = new TipoPrenda();
        alhaja.setId(1);
        alhaja.setTipo("ALHAJA");
        when(tipoPrendaRepository.findById(1)).thenReturn(Optional.of(alhaja));

        when(plazoParametroRepository.findByPlazoIdAndTipoPrendaIdAndSucursalId(1L, 1, 1))
                .thenReturn(Optional.empty());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private ContratoRequest buildRequestBase() {
        ContratoRequest request = new ContratoRequest();
        request.setIdCliente(1);
        request.setIdPlazo(1L);
        request.setPartidas(new ArrayList<>());
        return request;
    }

    private PartidaContratoRequest buildPartidaAlhaja(BigDecimal avaluoRealSpoofed, Integer kilataje,
                                                       String hechura, BigDecimal pesoGramos,
                                                       BigDecimal montoPrestamo) {
        PartidaContratoRequest pr = new PartidaContratoRequest();
        pr.setIdTipoPrenda(1);
        pr.setDescripcion("Anillo de oro");
        pr.setKilataje(kilataje);
        pr.setHechura(hechura);
        pr.setPesoGramos(pesoGramos);
        pr.setAvaluoReal(avaluoRealSpoofed);
        pr.setMontoPrestamo(montoPrestamo);
        return pr;
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    void crearContrato_partidaAlhaja_ignoraAvaluoRealDelCliente() {
        // Given: avaluoReal spoofed muy por encima del real calculado por el servidor
        ContratoRequest request = buildRequestBase();
        request.getPartidas().add(buildPartidaAlhaja(
                new BigDecimal("999999.00"), 14, "N",
                new BigDecimal("10.0000"), new BigDecimal("500.00")));

        PlazoHechuraAlhaja tabla = new PlazoHechuraAlhaja();
        tabla.setId(new PlazoHechuraAlhajaId(1, 1, 14, "N"));
        tabla.setPrecioPrestamo(new BigDecimal("1200.0000"));
        when(plazoHechuraAlhajaRepository.findById(new PlazoHechuraAlhajaId(1, 1, 14, "N")))
                .thenReturn(Optional.of(tabla));

        when(contratoMapper.toResponse(any(Contrato.class)))
                .thenAnswer(inv -> new ContratoResponse());
        when(repository.save(any(Contrato.class))).thenAnswer(inv -> {
            Contrato c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(1L);
            }
            return c;
        });

        // When
        contratoService.crearContrato(request, "cajero1");

        // Then: el avaluoReal persistido es el calculado por el servidor, NO el spoofed
        ArgumentCaptor<Contrato> captor = ArgumentCaptor.forClass(Contrato.class);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        Contrato captured = captor.getAllValues().get(0);
        BigDecimal avaluoRealPersistido = captured.getPartidas().get(0).getAvaluoReal();

        assertThat(avaluoRealPersistido.compareTo(new BigDecimal("999999.00"))).isNotEqualTo(0);
        assertThat(avaluoRealPersistido.compareTo(new BigDecimal("12000.00"))).isEqualTo(0);
    }

    @Test
    void crearContrato_partidaAlhaja_kilataje24K_rechazaConMensajeClaro() {
        // Given
        ContratoRequest request = buildRequestBase();
        request.getPartidas().add(buildPartidaAlhaja(
                new BigDecimal("1.00"), 24, "N",
                new BigDecimal("5.0000"), new BigDecimal("100.00")));

        // When / Then
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> contratoService.crearContrato(request, "cajero1"));
        assertThat(ex.getMessage()).isEqualTo("Oro de 24K no es prendable");
    }

    @Test
    void crearContrato_partidaAlhaja_kilatajeNoSoportado_rechaza() {
        // Given: 16K no está en la tabla COCAE de kilatajes soportados
        ContratoRequest request = buildRequestBase();
        request.getPartidas().add(buildPartidaAlhaja(
                new BigDecimal("1.00"), 16, "N",
                new BigDecimal("5.0000"), new BigDecimal("100.00")));

        // When / Then
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> contratoService.crearContrato(request, "cajero1"));
        assertThat(ex.getMessage()).isEqualTo("Kilataje no soportado: 16");
    }

    @Test
    void crearContrato_partidaAlhaja_montoPrestamoSuperaMaximoCalculadoPorServidor_rechaza() {
        // Given: misma celda del test 1 (avaluo real servidor = 12000.00), pero montoPrestamo
        // "coincide" con el avaluoReal spoofed del cliente (999999.00), que excede el techo real
        ContratoRequest request = buildRequestBase();
        request.getPartidas().add(buildPartidaAlhaja(
                new BigDecimal("999999.00"), 14, "N",
                new BigDecimal("10.0000"), new BigDecimal("999999.00")));

        PlazoHechuraAlhaja tabla = new PlazoHechuraAlhaja();
        tabla.setId(new PlazoHechuraAlhajaId(1, 1, 14, "N"));
        tabla.setPrecioPrestamo(new BigDecimal("1200.0000"));
        when(plazoHechuraAlhajaRepository.findById(new PlazoHechuraAlhajaId(1, 1, 14, "N")))
                .thenReturn(Optional.of(tabla));

        // When / Then
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> contratoService.crearContrato(request, "cajero1"));
        assertThat(ex.getMessage()).contains("supera el máximo autorizado");
    }
}
