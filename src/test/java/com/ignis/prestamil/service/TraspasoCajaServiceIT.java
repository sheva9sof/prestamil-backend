package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.exception.ForbiddenException;
import com.ignis.prestamil.mapper.CajaMapperImpl;
import com.ignis.prestamil.model.Caja;
import com.ignis.prestamil.model.ConceptoCaja;
import com.ignis.prestamil.model.Configuracion;
import com.ignis.prestamil.model.Denominacion;
import com.ignis.prestamil.model.EstatusTraspasoCaja;
import com.ignis.prestamil.model.FolioSecuencia;
import com.ignis.prestamil.model.MovimientoCaja;
import com.ignis.prestamil.model.Rol;
import com.ignis.prestamil.model.SerieFolio;
import com.ignis.prestamil.model.TipoCaja;
import com.ignis.prestamil.model.TipoDenominacion;
import com.ignis.prestamil.model.TipoTraspasoCaja;
import com.ignis.prestamil.model.TraspasoCaja;
import com.ignis.prestamil.model.Turno;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.CajaRepository;
import com.ignis.prestamil.repository.ConfiguracionRepository;
import com.ignis.prestamil.repository.DenominacionRepository;
import com.ignis.prestamil.repository.FolioSecuenciaRepository;
import com.ignis.prestamil.repository.MovimientoCajaRepository;
import com.ignis.prestamil.repository.TraspasoCajaRepository;
import com.ignis.prestamil.repository.TurnoRepository;
import com.ignis.prestamil.repository.UsuarioRepository;
import com.ignis.prestamil.request.CancelarTraspasoCajaRequest;
import com.ignis.prestamil.request.TraspasoCajaDenominacionRequest;
import com.ignis.prestamil.request.TraspasoCajaRequest;
import com.ignis.prestamil.response.TraspasoCajaDenominacionResponse;
import com.ignis.prestamil.response.TraspasoCajaResponse;
import com.ignis.prestamil.util.Constantes;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * Traspasos entre cajas (CAJ-02) contra H2, con las consultas de saldo, el folio y los bloqueos reales.
 * Los roles y las configuraciones son los de 002 y 079: 1 Sistemas, 3 Cajero, 5 Gerente.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TraspasoCajaService.class, SaldoCajaService.class, FolioService.class, CajaMapperImpl.class,
        TraspasoCajaServiceIT.Reloj.class})
class TraspasoCajaServiceIT {

    private static final int SUCURSAL = 1;
    private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");
    private static final LocalDate HOY = LocalDate.of(2026, 10, 30);
    private static final String MOTIVO = "Se capturó el importe equivocado";

    @TestConfiguration
    static class Reloj {
        @Bean
        Clock clock() {
            return Clock.fixed(HOY.atTime(10, 0).atZone(ZONA).toInstant(), ZONA);
        }
    }

    @Autowired TraspasoCajaService traspasoCajaService;
    @Autowired SaldoCajaService saldoCajaService;
    @Autowired TraspasoCajaRepository traspasoCajaRepository;
    @Autowired CajaRepository cajaRepository;
    @Autowired DenominacionRepository denominacionRepository;
    @Autowired ConfiguracionRepository configuracionRepository;
    @Autowired FolioSecuenciaRepository folioSecuenciaRepository;
    @Autowired MovimientoCajaRepository movimientoCajaRepository;
    @Autowired TurnoRepository turnoRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired EntityManager em;

    private Caja concentradora;
    private Caja boveda;
    private Caja ventanilla;
    /** Denominación por valor ("1000", "0.50", ...). */
    private final Map<String, Denominacion> denominaciones = new HashMap<>();
    private Usuario sistemas;
    private Usuario gerente;
    private Usuario cajero;

    @BeforeEach
    void sembrar() {
        concentradora = caja(TipoCaja.CONCENTRADORA, "001", "CUENTA CONCENTRADORA");
        boveda = caja(TipoCaja.BOVEDA, "002", "BÓVEDA");
        ventanilla = caja(TipoCaja.VENTANILLA, "003", "VENTANILLA");

        // Las 14 de COCAE (074) con el ajuste de 081 (Q-10): 0.01 inactiva al final y 0.05 en el lugar 14
        String[] valores = {"1000", "500", "200", "100", "50", "20", "10", "5", "2", "1", "0.50", "0.20", "0.10", "0.05", "0.01"};
        for (int i = 0; i < valores.length; i++) {
            Denominacion d = new Denominacion();
            d.setValor(new BigDecimal(valores[i]).setScale(2));
            d.setTipo(i < 6 ? TipoDenominacion.BILLETE : TipoDenominacion.MONEDA);
            d.setOrden(i + 1);
            d.setActiva(!"0.01".equals(valores[i]));
            denominaciones.put(valores[i], denominacionRepository.save(d));
        }

        sistemas = usuario("sistemas", rol(1, "Sistemas"));
        cajero = usuario("cajero", rol(3, "Cajero"));
        gerente = usuario("gerente", rol(5, "Gerente"));

        configuracion(Constantes.ROLES_PERMITIDOS_MINISTRACION, "5,3,1");  // 080 (Q-06)
        configuracion(Constantes.ROLES_PERMITIDOS_CONCENTRACION, "5,1");
        configuracion(Constantes.ROLES_PERMITIDOS_DOTACION, "5,3,1");
        configuracion(Constantes.ROLES_PERMITIDOS_RECOLECCION, "5,3,1");
        configuracion(Constantes.ROLES_PERMITIDOS_CANCELAR_TRASPASO, "1");
    }

    @Test
    void cuatroTickets_4353a4356_foliosCajasEImportes() {
        // El contador de COCAE iba en 4352; la bóveda traía 5,000 de días anteriores (RC-08)
        FolioSecuencia folio = new FolioSecuencia();
        folio.setSucursalId(SUCURSAL);
        folio.setSerie(SerieFolio.TRASPASO_CAJA);
        folio.setUltimoFolio(4352L);
        folioSecuenciaRepository.saveAndFlush(folio);
        traspasoAnterior(TipoTraspasoCaja.MINISTRACION, concentradora, boveda, "5000.00");
        Turno turno = turnoActivo(cajero);

        TraspasoCajaResponse ministracion = traspasoCajaService.registrar(SUCURSAL,
                request(TipoTraspasoCaja.MINISTRACION, "1000", null, "1000", 1), "gerente");
        TraspasoCajaResponse concentracion = traspasoCajaService.registrar(SUCURSAL,
                request(TipoTraspasoCaja.CONCENTRACION, "2000", "  FICHA-778812 ", "500", 2, "200", 5),
                "gerente");
        TraspasoCajaResponse dotacion = traspasoCajaService.registrar(SUCURSAL,
                request(TipoTraspasoCaja.DOTACION, "1000", null, "100", 5, "50", 8, "20", 5),
                "cajero");
        TraspasoCajaResponse recoleccion = traspasoCajaService.registrar(SUCURSAL,
                request(TipoTraspasoCaja.RECOLECCION, "1000.00", null,
                        "500", 1, "100", 4, "50", 1, "10", 4, "5", 1, "2", 2, "0.50", 1, "0.20", 2, "0.10", 1),
                "cajero");

        assertTicket(ministracion, 4353L, TipoTraspasoCaja.MINISTRACION, "001", "002", "1000.00");
        assertTicket(concentracion, 4354L, TipoTraspasoCaja.CONCENTRACION, "002", "001", "2000.00");
        assertTicket(dotacion, 4355L, TipoTraspasoCaja.DOTACION, "002", "003", "1000.00");
        assertTicket(recoleccion, 4356L, TipoTraspasoCaja.RECOLECCION, "003", "002", "1000.00");

        assertThat(concentracion.getReferenciaBancaria()).isEqualTo("FICHA-778812");
        assertThat(ministracion.getReferenciaBancaria()).isNull();
        assertThat(dotacion.getNombreUsuario()).isEqualTo("cajero");
        assertThat(dotacion.getTurnoId()).isEqualTo(turno.getId());
        // Cada renglón con su importe calculado en el servidor
        assertThat(concentracion.getDesglose())
                .extracting(TraspasoCajaDenominacionResponse::getCantidad, TraspasoCajaDenominacionResponse::getImporte)
                .containsExactly(
                        tuple(2, new BigDecimal("1000.00")),
                        tuple(5, new BigDecimal("1000.00")));

        // 5,000 + 1,000 − 2,000 − 1,000 + 1,000; la ventanilla recibió y entregó 1,000
        em.flush();
        assertThat(saldoCajaService.saldoActual(boveda)).isEqualByComparingTo("4000.00");
        assertThat(saldoCajaService.saldoActual(ventanilla)).isEqualByComparingTo("0.00");

        // La lista del día no trae el traspaso de ayer
        assertThat(traspasoCajaService.listar(SUCURSAL, null, null))
                .extracting(TraspasoCajaResponse::getFolio)
                .containsExactly(4353L, 4354L, 4355L, 4356L);
        assertThat(traspasoCajaService.listar(SUCURSAL, HOY, TipoTraspasoCaja.DOTACION))
                .extracting(TraspasoCajaResponse::getFolio)
                .containsExactly(4355L);
    }

    @Test
    void desgloseQueNoCuadra_400YNoGuardaNiConsumeFolio() {
        traspasoAnterior(TipoTraspasoCaja.MINISTRACION, concentradora, boveda, "5000.00");

        // 1×500 + 4×100 = 900, la pantalla dice 1,000
        assertThatThrownBy(() -> traspasoCajaService.registrar(SUCURSAL,
                request(TipoTraspasoCaja.DOTACION, "1000", null, "500", 1, "100", 4), "cajero"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no coincide con la suma del desglose");

        assertThat(traspasoCajaService.listar(SUCURSAL, HOY, null)).isEmpty();
        assertThat(folioSecuenciaRepository.findAll()).isEmpty();
    }

    @Test
    void desgloseConDenominacionRepetida_400() {
        traspasoAnterior(TipoTraspasoCaja.MINISTRACION, concentradora, boveda, "5000.00");

        assertThatThrownBy(() -> traspasoCajaService.registrar(SUCURSAL,
                request(TipoTraspasoCaja.DOTACION, "1000", null, "500", 1, "500", 1), "cajero"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("repetida");
    }

    @Test
    void dotacionMayorQueLaBoveda_400() {
        traspasoAnterior(TipoTraspasoCaja.MINISTRACION, concentradora, boveda, "500.00");

        assertThatThrownBy(() -> traspasoCajaService.registrar(SUCURSAL,
                request(TipoTraspasoCaja.DOTACION, "1000", null, "1000", 1), "cajero"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("BÓVEDA en negativo");
    }

    @Test
    void recoleccionMayorQueLaVentanilla_400_cuentaElEfectivoDelDiaYNoLaTarjeta() {
        traspasoAnterior(TipoTraspasoCaja.MINISTRACION, concentradora, boveda, "5000.00");
        Turno turno = turnoActivo(cajero);
        traspasoCajaService.registrar(SUCURSAL,
                request(TipoTraspasoCaja.DOTACION, "1000", null, "1000", 1), "cajero");
        // Empeño de 800 en efectivo; un refrendo de 300 pagado con tarjeta no entra a la gaveta (RC-06)
        flujo(turno, ConceptoCaja.EMPENO, "800.00", "0.00");
        flujo(turno, ConceptoCaja.REFRENDO, "0.00", "300.00");
        em.flush();
        assertThat(saldoCajaService.saldoActual(ventanilla)).isEqualByComparingTo("200.00");

        assertThatThrownBy(() -> traspasoCajaService.registrar(SUCURSAL,
                request(TipoTraspasoCaja.RECOLECCION, "500", null, "500", 1), "cajero"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("VENTANILLA en negativo");

        // Lo que sí hay se puede recolectar
        TraspasoCajaResponse recoleccion = traspasoCajaService.registrar(SUCURSAL,
                request(TipoTraspasoCaja.RECOLECCION, "200", null, "200", 1), "cajero");
        assertThat(recoleccion.getEstatus()).isEqualTo(EstatusTraspasoCaja.ACTIVO);
    }

    @Test
    void ministracionDesdeLaConcentradora_noValidaSaldoDelBanco() {
        TraspasoCajaResponse ministracion = traspasoCajaService.registrar(SUCURSAL,
                request(TipoTraspasoCaja.MINISTRACION, "20000", null, "1000", 20), "sistemas");

        assertThat(ministracion.getFolio()).isEqualTo(1L);
        em.flush();
        assertThat(saldoCajaService.saldoActual(boveda)).isEqualByComparingTo("20000.00");
    }

    @Test
    void rolNoPermitido_403() {
        traspasoAnterior(TipoTraspasoCaja.MINISTRACION, concentradora, boveda, "5000.00");

        // El cajero no puede concentrar
        assertThatThrownBy(() -> traspasoCajaService.registrar(SUCURSAL,
                request(TipoTraspasoCaja.CONCENTRACION, "1000", null, "1000", 1), "cajero"))
                .isInstanceOf(ForbiddenException.class);
        // Un rol fuera de toda configuración tampoco dota
        usuario("valuador", rol(4, "Valuador"));
        assertThatThrownBy(() -> traspasoCajaService.registrar(SUCURSAL,
                request(TipoTraspasoCaja.DOTACION, "1000", null, "1000", 1), "valuador"))
                .isInstanceOf(ForbiddenException.class);

        assertThat(traspasoCajaService.listar(SUCURSAL, HOY, null)).isEmpty();
    }

    @Test
    void ministracion_laRegistraElCajeroOElGerente_Q06() {
        TraspasoCajaResponse delCajero = traspasoCajaService.registrar(SUCURSAL,
                request(TipoTraspasoCaja.MINISTRACION, "1000", null, "1000", 1), "cajero");
        TraspasoCajaResponse delGerente = traspasoCajaService.registrar(SUCURSAL,
                request(TipoTraspasoCaja.MINISTRACION, "500", null, "500", 1), "gerente");

        assertThat(delCajero.getNombreUsuario()).isEqualTo("cajero");
        assertThat(delGerente.getNombreUsuario()).isEqualTo("gerente");
        em.flush();
        assertThat(saldoCajaService.saldoActual(boveda)).isEqualByComparingTo("1500.00");
    }

    @Test
    void denominaciones_soloActivas_sinUnCentavoYConCincoCentavos_Q10() {
        assertThat(traspasoCajaService.denominacionesActivas())
                .hasSize(14)
                .extracting(d -> d.getValor().toPlainString())
                .startsWith("1000.00")
                .endsWith("0.10", "0.05")
                .doesNotContain("0.01");

        assertThatThrownBy(() -> traspasoCajaService.registrar(SUCURSAL,
                request(TipoTraspasoCaja.MINISTRACION, "1000.01", null, "1000", 1, "0.01", 1), "gerente"))
                .isInstanceOf(BadRequestException.class);
        assertThat(traspasoCajaService.registrar(SUCURSAL,
                request(TipoTraspasoCaja.MINISTRACION, "1000.05", null, "1000", 1, "0.05", 1), "gerente")
                .getImporte()).isEqualByComparingTo("1000.05");
    }

    @Test
    void cancelacion_soloSistemas_regresaElDineroYQuedaMarcada() {
        traspasoAnterior(TipoTraspasoCaja.MINISTRACION, concentradora, boveda, "5000.00");
        TraspasoCajaResponse dotacion = traspasoCajaService.registrar(SUCURSAL,
                request(TipoTraspasoCaja.DOTACION, "1000", null, "1000", 1), "cajero");

        // TODO A-05: el gerente podrá cancelar con autorización; hoy solo Sistemas
        assertThatThrownBy(() -> traspasoCajaService.cancelar(dotacion.getId(), motivo(MOTIVO), "gerente"))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> traspasoCajaService.cancelar(dotacion.getId(), motivo("corto"), "sistemas"))
                .isInstanceOf(BadRequestException.class);

        TraspasoCajaResponse cancelado = traspasoCajaService.cancelar(dotacion.getId(), motivo(MOTIVO), "sistemas");

        assertThat(cancelado.getEstatus()).isEqualTo(EstatusTraspasoCaja.CANCELADO);
        assertThat(cancelado.getNombreUsuarioCancela()).isEqualTo("sistemas");
        assertThat(cancelado.getMotivoCancelacion()).isEqualTo(MOTIVO);
        assertThat(cancelado.getFechaCancelacion()).isEqualTo(HOY.atTime(10, 0));
        em.flush();
        assertThat(saldoCajaService.saldoActual(boveda)).isEqualByComparingTo("5000.00");
        assertThat(saldoCajaService.saldoActual(ventanilla)).isEqualByComparingTo("0.00");
        // Sigue en la lista del día, marcada
        assertThat(traspasoCajaService.listar(SUCURSAL, HOY, null))
                .extracting(TraspasoCajaResponse::getEstatus)
                .containsExactly(EstatusTraspasoCaja.CANCELADO);

        assertThatThrownBy(() -> traspasoCajaService.cancelar(dotacion.getId(), motivo(MOTIVO), "sistemas"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ya está cancelado");
    }

    @Test
    void cancelacion_deDotacionYaGastada_400() {
        traspasoAnterior(TipoTraspasoCaja.MINISTRACION, concentradora, boveda, "5000.00");
        Turno turno = turnoActivo(cajero);
        TraspasoCajaResponse dotacion = traspasoCajaService.registrar(SUCURSAL,
                request(TipoTraspasoCaja.DOTACION, "1000", null, "1000", 1), "cajero");
        flujo(turno, ConceptoCaja.EMPENO, "600.00", "0.00");
        em.flush();

        // La ventanilla ya solo tiene 400: no puede devolver los 1,000 a la bóveda
        assertThatThrownBy(() -> traspasoCajaService.cancelar(dotacion.getId(), motivo(MOTIVO), "sistemas"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("VENTANILLA en negativo");
    }

    @Test
    void cancelacion_deOtroDia_400() {
        TraspasoCaja ayer = traspasoAnterior(TipoTraspasoCaja.MINISTRACION, concentradora, boveda, "5000.00");

        assertThatThrownBy(() -> traspasoCajaService.cancelar(ayer.getId(), motivo(MOTIVO), "sistemas"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("día en curso");
    }

    // ---------------------------------------------------------------- helpers

    private static void assertTicket(TraspasoCajaResponse t, Long folio, TipoTraspasoCaja tipo,
                                     String origen, String destino, String importe) {
        assertThat(t.getFolio()).isEqualTo(folio);
        assertThat(t.getTipo()).isEqualTo(tipo);
        assertThat(t.getCajaOrigenClave()).isEqualTo(origen);
        assertThat(t.getCajaDestinoClave()).isEqualTo(destino);
        assertThat(t.getImporte()).isEqualByComparingTo(importe);
        assertThat(t.getFechaOperacion()).isEqualTo(HOY);
        assertThat(t.getEstatus()).isEqualTo(EstatusTraspasoCaja.ACTIVO);
        assertThat(t.getDesglose().stream()
                .map(TraspasoCajaDenominacionResponse::getImporte)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(importe);
    }

    /** Desglose como pares valor, cantidad: {@code "500", 2, "200", 5}. */
    private TraspasoCajaRequest request(TipoTraspasoCaja tipo, String importe, String referencia, Object... desglose) {
        List<TraspasoCajaDenominacionRequest> renglones = new ArrayList<>();
        for (int i = 0; i < desglose.length; i += 2) {
            TraspasoCajaDenominacionRequest r = new TraspasoCajaDenominacionRequest();
            r.setDenominacionId(denominaciones.get((String) desglose[i]).getId());
            r.setCantidad((Integer) desglose[i + 1]);
            renglones.add(r);
        }
        TraspasoCajaRequest request = new TraspasoCajaRequest();
        request.setTipo(tipo);
        request.setImporte(new BigDecimal(importe));
        request.setDesglose(renglones);
        request.setReferenciaBancaria(referencia);
        return request;
    }

    private static CancelarTraspasoCajaRequest motivo(String motivo) {
        CancelarTraspasoCajaRequest r = new CancelarTraspasoCajaRequest();
        r.setMotivo(motivo);
        return r;
    }

    private TraspasoCaja traspasoAnterior(TipoTraspasoCaja tipo, Caja origen, Caja destino, String importe) {
        TraspasoCaja t = new TraspasoCaja();
        t.setSucursalId(SUCURSAL);
        t.setFolio(1000L + traspasoCajaRepository.count());
        t.setTipo(tipo);
        t.setCajaOrigen(origen);
        t.setCajaDestino(destino);
        t.setImporte(new BigDecimal(importe));
        t.setFechaOperacion(HOY.minusDays(1));
        t.setFechaRegistro(HOY.minusDays(1).atTime(9, 0));
        t.setUsuario(gerente);
        return traspasoCajaRepository.saveAndFlush(t);
    }

    private void flujo(Turno turno, ConceptoCaja concepto, String efectivo, String tarjeta) {
        MovimientoCaja m = new MovimientoCaja();
        m.setTurno(turno);
        m.setSucursalId(SUCURSAL);
        m.setTipo(concepto.tipo());
        m.setConceptoCaja(concepto);
        m.setConcepto(concepto.name());
        m.setImporteEfectivo(new BigDecimal(efectivo));
        m.setImporteTarjeta(new BigDecimal(tarjeta));
        m.setMonto(m.getImporteEfectivo().add(m.getImporteTarjeta()));
        m.setUsuario(cajero);
        m.setFecha(HOY.atTime(11, 0));
        movimientoCajaRepository.save(m);
    }

    private Caja caja(TipoCaja tipo, String clave, String nombre) {
        Caja c = new Caja();
        c.setSucursalId(SUCURSAL);
        c.setTipo(tipo);
        c.setClave(clave);
        c.setNombre(nombre);
        return cajaRepository.save(c);
    }

    /** Rol con el id fijo de 002-initial-data, para que las configuraciones sean las del changeset 079. */
    private Rol rol(int id, String nombre) {
        em.createNativeQuery("INSERT INTO roles (id, rol, estatus) VALUES (?1, ?2, TRUE)")
                .setParameter(1, id)
                .setParameter(2, nombre)
                .executeUpdate();
        return em.find(Rol.class, id);
    }

    private Usuario usuario(String nombreUsuario, Rol rol) {
        Usuario u = new Usuario();
        u.setNombreUsuario(nombreUsuario);
        u.setPassword("x");
        u.setNombre(nombreUsuario);
        u.setApellidos("Prueba");
        u.setEstatus(true);
        u.setCreado(HOY.atStartOfDay());
        u.setRol(rol);
        u.setVigencia(true);
        u.setFechaCambioPass(HOY);
        return usuarioRepository.save(u);
    }

    private Turno turnoActivo(Usuario usuario) {
        Turno t = new Turno();
        t.setUsuario(usuario);
        t.setFechaInicio(HOY.atTime(8, 0));
        t.setActivo(true);
        return turnoRepository.save(t);
    }

    private void configuracion(String clave, String csv) {
        Configuracion c = new Configuracion();
        c.setConfiguracion(clave);
        c.setValorCadena(csv);
        configuracionRepository.save(c);
    }
}
