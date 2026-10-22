package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.Cliente;
import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.Direccion;
import com.ignis.prestamil.model.EstatusContrato;
import com.ignis.prestamil.model.EstatusPartida;
import com.ignis.prestamil.model.FiltroEstatusOperacion;
import com.ignis.prestamil.model.PartidaContrato;
import com.ignis.prestamil.model.Plazo;
import com.ignis.prestamil.model.Rol;
import com.ignis.prestamil.model.TipoPrenda;
import com.ignis.prestamil.model.Turno;
import com.ignis.prestamil.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ejecuta en H2 los filtros del listado de Finiquitos y Refrendos (F2). Cada contrato del fixture
 * representa un estatus; el folio dice cuál.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ContratoSpecificationsIT {

    @Autowired TestEntityManager em;
    @Autowired ContratoRepository contratoRepository;

    final LocalDate hoy = LocalDate.now();

    Usuario usuario;
    Turno turno;
    Plazo plazo;
    TipoPrenda alhaja;
    TipoPrenda varios;
    Cliente juan;
    Cliente maria;

    @BeforeEach
    void fixture() {
        Rol rol = new Rol();
        rol.setRol("Cajero");
        rol.setEstatus(true);
        em.persist(rol);

        usuario = new Usuario();
        usuario.setNombreUsuario("cajero1");
        usuario.setNombre("Caja");
        usuario.setApellidos("Uno");
        usuario.setPassword("$2a$10$hashplaceholder");
        usuario.setEstatus(true);
        usuario.setCreado(LocalDateTime.now());
        usuario.setRol(rol);
        usuario.setVigencia(true);
        usuario.setFechaCambioPass(hoy);
        usuario.setEditable(true);
        em.persist(usuario);

        turno = new Turno();
        turno.setUsuario(usuario);
        turno.setFechaInicio(LocalDateTime.now());
        turno.setActivo(true);
        em.persist(turno);

        plazo = new Plazo();
        plazo.setNombre("Semanal");
        plazo.setDiasPorPeriodo(7);
        plazo.setNumeroPeriodos(4);
        em.persist(plazo);

        alhaja = tipoPrenda(1, "ALHAJA");
        varios = tipoPrenda(3, "VARIOS");
        juan = cliente("Juan", "Pérez", "López", "7770000001");
        maria = cliente("María", "Gómez", "Ruiz", "7770000002");

        contrato("VIGENTE", juan, alhaja, hoy.plusDays(10), EstatusContrato.VIGENTE, 0, EstatusPartida.OP, 1);
        contrato("REFRENDADO", maria, varios, hoy.plusDays(3), EstatusContrato.VIGENTE, 2, EstatusPartida.OP, 1);
        contrato("GRACIA", juan, alhaja, hoy.minusDays(1), EstatusContrato.VIGENTE, 0, EstatusPartida.OP, 1);
        contrato("VENCIDO", juan, alhaja, hoy.minusDays(5), EstatusContrato.VENCIDO, 1, EstatusPartida.OP, 1);
        contrato("VENTA-FECHA", maria, alhaja, hoy.minusDays(20), EstatusContrato.VIGENTE, 0, EstatusPartida.OP, 1);
        // Pase a venta anticipado: el estatus persistido manda aunque la fecha siga vigente
        contrato("VENTA-PASE", maria, alhaja, hoy.plusDays(5), EstatusContrato.EN_VENTA, 0, EstatusPartida.OP, 1);
        contrato("APARTADO", juan, alhaja, hoy.minusDays(20), EstatusContrato.EN_VENTA, 0, EstatusPartida.APA, 1);
        contrato("VENDIDO", juan, alhaja, hoy.minusDays(40), EstatusContrato.VENDIDO, 0, EstatusPartida.VEN, 1);
        contrato("FINIQUITADO", juan, alhaja, hoy.plusDays(8), EstatusContrato.FINIQUITADO, 3, EstatusPartida.FIN, 1);
        contrato("CANCELADO", maria, varios, hoy.plusDays(8), EstatusContrato.CANCELADO, 0, EstatusPartida.OP, 1);
        contrato("OTRA-SUC", juan, alhaja, hoy.plusDays(10), EstatusContrato.VIGENTE, 0, EstatusPartida.OP, 2);
        em.flush();
        em.clear();
    }

    // =========================================================================
    // Fixtures
    // =========================================================================

    private TipoPrenda tipoPrenda(int id, String tipo) {
        TipoPrenda t = new TipoPrenda();
        t.setId(id);
        t.setTipo(tipo);
        return em.persist(t);
    }

    private Cliente cliente(String nombre, String apPat, String apMat, String telefono) {
        Direccion d = new Direccion();
        d.setTipoDireccion(Direccion.TipoDireccion.Particular);
        d.setCalle("Calle");
        d.setNumeroExterior("1");
        d.setColonia("Centro");
        d.setCiudad("Cuernavaca");
        d.setEstado("Morelos");
        d.setCodigoPostal("62000");
        d.setFechaRegistro(hoy);
        d.setEsVerificada(false);
        em.persist(d);

        Cliente c = new Cliente();
        c.setNombre(nombre);
        c.setApellidoPaterno(apPat);
        c.setApellidoMaterno(apMat);
        c.setTelefono(telefono);
        c.setActivo(true);
        c.setDireccion(d);
        return em.persist(c);
    }

    private void contrato(String folio, Cliente cliente, TipoPrenda tipo, LocalDate vencimiento,
                          EstatusContrato estatus, int refrendos, EstatusPartida estatusPartida, int sucursal) {
        Contrato c = new Contrato();
        c.setFolio(folio);
        c.setCliente(cliente);
        c.setTurno(turno);
        c.setUsuario(usuario);
        c.setPlazo(plazo);
        c.setSucursalId(sucursal);
        c.setFechaApertura(vencimiento.minusDays(28).atStartOfDay());
        c.setFechaContrato(vencimiento.minusDays(28));
        c.setFechaVencimiento(vencimiento);
        c.setFechaComercializacion(vencimiento.plusDays(15));
        c.setMontoPrestamo(new BigDecimal("1000.00"));
        c.setSaldoCapital(new BigDecimal("1000.00"));
        c.setMontoAvaluo(new BigDecimal("1300.00"));
        c.setEstatus(estatus);
        c.setNumRefrendos(refrendos);

        PartidaContrato p = new PartidaContrato();
        p.setContrato(c);
        p.setNumPartida(1);
        p.setTipoPrenda(tipo);
        p.setDescripcion("Prenda de " + folio);
        p.setAvaluoReal(c.getMontoAvaluo());
        p.setAvaluoContrato(c.getMontoAvaluo());
        p.setMontoPrestamo(c.getMontoPrestamo());
        p.setEstatus(estatusPartida);
        c.setPartidas(new ArrayList<>(List.of(p)));
        em.persist(c);
    }

    private List<String> folios(Specification<Contrato> filtro) {
        Specification<Contrato> spec = Specification.where(ContratoSpecifications.deSucursal(1)).and(filtro);
        return contratoRepository.findAll(spec, Sort.by("folio")).stream().map(Contrato::getFolio).toList();
    }

    private List<String> folios(FiltroEstatusOperacion filtro) {
        return folios(ContratoSpecifications.porEstatus(filtro, hoy));
    }

    // =========================================================================
    // Estatus
    // =========================================================================

    @Test
    void todosSoloDeLaSucursal() {
        assertThat(folios(FiltroEstatusOperacion.TODOS)).hasSize(10).doesNotContain("OTRA-SUC");
    }

    @Test
    void enOperacionSonLosNoVencidosSinPaseAVenta() {
        assertThat(folios(FiltroEstatusOperacion.EN_OPERACION)).containsExactly("REFRENDADO", "VIGENTE");
    }

    @Test
    void graciaYVencidosTraenLaVentanaEntreVencimientoYComercializacion() {
        // El servicio separa gracia de vencidos con los días de gracia de cada contrato
        assertThat(folios(FiltroEstatusOperacion.PERIODO_GRACIA)).containsExactly("GRACIA", "VENCIDO");
        assertThat(folios(FiltroEstatusOperacion.VENCIDOS)).containsExactly("GRACIA", "VENCIDO");
    }

    @Test
    void enVentaPorFechaOPorPaseSinPrendasApartadas() {
        assertThat(folios(FiltroEstatusOperacion.EN_VENTA)).containsExactly("VENTA-FECHA", "VENTA-PASE");
    }

    @Test
    void refrendadosAbiertos() {
        assertThat(folios(FiltroEstatusOperacion.REFRENDADOS)).containsExactly("REFRENDADO", "VENCIDO");
    }

    @Test
    void vendidosIncluyenApartados() {
        assertThat(folios(FiltroEstatusOperacion.VENDIDOS)).containsExactly("APARTADO", "VENDIDO");
    }

    @Test
    void finiquitadosYCancelados() {
        assertThat(folios(FiltroEstatusOperacion.FINIQUITADOS)).containsExactly("FINIQUITADO");
        assertThat(folios(FiltroEstatusOperacion.CANCELADOS)).containsExactly("CANCELADO");
    }

    // =========================================================================
    // Búsqueda y ramo
    // =========================================================================

    @Test
    void porContratoNumericoBuscaElIdYSiNoElFolio() {
        Long id = contratoRepository.findByFolio("GRACIA").orElseThrow().getId();
        assertThat(folios(ContratoSpecifications.porContrato(id.toString()))).containsExactly("GRACIA");
        assertThat(folios(ContratoSpecifications.porContrato("venta"))).containsExactly("VENTA-FECHA", "VENTA-PASE");
    }

    @Test
    void porNombreClienteCadaPalabraEnNombreOApellidos() {
        assertThat(folios(ContratoSpecifications.porNombreCliente("maría ruiz")))
                .containsExactly("CANCELADO", "REFRENDADO", "VENTA-FECHA", "VENTA-PASE");
        assertThat(folios(ContratoSpecifications.porNombreCliente("juan ruiz"))).isEmpty();
    }

    @Test
    void porNumeroDeClienteYFechaDeContrato() {
        assertThat(folios(ContratoSpecifications.deCliente(maria.getId())))
                .containsExactly("CANCELADO", "REFRENDADO", "VENTA-FECHA", "VENTA-PASE");
        assertThat(folios(ContratoSpecifications.conFechaContrato(hoy.minusDays(29)))).containsExactly("GRACIA");
    }

    @Test
    void ramoFiltraPorTipoDePrenda() {
        assertThat(folios(ContratoSpecifications.deRamo(varios.getId()))).containsExactly("CANCELADO", "REFRENDADO");
    }

    @Test
    void paginaConConteoYFiltrosCombinados() {
        Specification<Contrato> spec = Specification.where(ContratoSpecifications.deSucursal(1))
                .and(ContratoSpecifications.deRamo(alhaja.getId()))
                .and(ContratoSpecifications.porEstatus(FiltroEstatusOperacion.EN_VENTA, hoy));

        Page<Contrato> pagina = contratoRepository.findAll(spec, PageRequest.of(0, 1, Sort.by("folio")));

        assertThat(pagina.getTotalElements()).isEqualTo(2);
        assertThat(pagina.getContent()).extracting(Contrato::getFolio).containsExactly("VENTA-FECHA");
        // cliente y plazo vienen en la misma consulta (EntityGraph)
        assertThat(pagina.getContent().get(0).getCliente().getNombre()).isEqualTo("María");
    }
}
