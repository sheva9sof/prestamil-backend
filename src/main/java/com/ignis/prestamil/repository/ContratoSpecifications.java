package com.ignis.prestamil.repository;

import com.ignis.prestamil.model.Contrato;
import com.ignis.prestamil.model.EstatusContrato;
import com.ignis.prestamil.model.EstatusPartida;
import com.ignis.prestamil.model.FiltroEstatusOperacion;
import com.ignis.prestamil.model.PartidaContrato;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/**
 * Filtros del listado de la pantalla de Finiquitos y Refrendos (F2). Replican en SQL las reglas de
 * {@link com.ignis.prestamil.service.calculo.EstatusContratoResolver} para poder paginar en la base.
 */
public final class ContratoSpecifications {

    private static final Pattern NUMERO = Pattern.compile("\\d{1,18}");

    /** Estatus persistidos que el resolver decide solo por fechas (VENCIDO lo escribirá el pase diario). */
    private static final List<EstatusContrato> ABIERTOS_POR_FECHA =
            List.of(EstatusContrato.VIGENTE, EstatusContrato.VENCIDO);

    private static final List<EstatusContrato> CERRADOS =
            List.of(EstatusContrato.FINIQUITADO, EstatusContrato.CANCELADO, EstatusContrato.VENDIDO);

    private ContratoSpecifications() {
    }

    public static Specification<Contrato> deSucursal(Integer sucursalId) {
        return (root, query, cb) -> cb.equal(root.get("sucursalId"), sucursalId);
    }

    /** Contratos con al menos una partida del tipo de prenda (ramo). */
    public static Specification<Contrato> deRamo(Integer tipoPrendaId) {
        return (root, query, cb) -> existePartida(root, query, cb,
                (p, b) -> b.equal(p.get("tipoPrenda").get("id"), tipoPrendaId));
    }

    /** Número de contrato (id) o folio exacto si es numérico; si no, folio que contenga el texto. */
    public static Specification<Contrato> porContrato(String texto) {
        return (root, query, cb) -> {
            Expression<String> folio = cb.upper(root.get("folio"));
            String buscado = texto.toUpperCase(Locale.ROOT);
            if (NUMERO.matcher(texto).matches()) {
                return cb.or(cb.equal(root.get("id"), Long.parseLong(texto)), cb.equal(folio, buscado));
            }
            return cb.like(folio, "%" + buscado + "%");
        };
    }

    public static Specification<Contrato> deCliente(Integer clienteId) {
        return (root, query, cb) -> cb.equal(root.get("cliente").get("id"), clienteId);
    }

    /** Cada palabra debe aparecer en el nombre o en alguno de los apellidos del cliente. */
    public static Specification<Contrato> porNombreCliente(String texto) {
        return (root, query, cb) -> {
            List<Predicate> palabras = new ArrayList<>();
            for (String palabra : texto.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
                String patron = "%" + palabra + "%";
                palabras.add(cb.or(
                        cb.like(cb.lower(root.get("cliente").get("nombre")), patron),
                        cb.like(cb.lower(root.get("cliente").get("apellidoPaterno")), patron),
                        cb.like(cb.lower(root.get("cliente").get("apellidoMaterno")), patron)));
            }
            return cb.and(palabras.toArray(Predicate[]::new));
        };
    }

    public static Specification<Contrato> conFechaContrato(LocalDate fecha) {
        return (root, query, cb) -> cb.equal(root.get("fechaContrato"), fecha);
    }

    /**
     * Filtro de estatus a la fecha {@code hoy}. PERIODO_GRACIA y VENCIDOS dependen de los días de gracia
     * de cada contrato (snapshot o parámetro vigente), así que aquí devuelven un superconjunto: los
     * contratos ya vencidos que aún no llegan a su fecha de comercialización. El servicio termina de
     * separarlos con el resolver.
     *
     * @return la especificación, o null para TODOS
     */
    public static Specification<Contrato> porEstatus(FiltroEstatusOperacion filtro, LocalDate hoy) {
        return switch (filtro) {
            case TODOS -> null;
            case EN_OPERACION -> abiertoPorFechas()
                    .and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("fechaVencimiento"), hoy));
            case PERIODO_GRACIA, VENCIDOS -> abiertoPorFechas()
                    .and((root, query, cb) -> cb.and(
                            cb.lessThan(root.get("fechaVencimiento"), hoy),
                            cb.greaterThan(root.get("fechaComercializacion"), hoy)));
            case EN_VENTA -> abierto()
                    .and((root, query, cb) -> cb.or(
                            cb.equal(root.get("estatus"), EstatusContrato.EN_VENTA),
                            cb.lessThanOrEqualTo(root.get("fechaComercializacion"), hoy)));
            case REFRENDADOS -> abierto()
                    .and((root, query, cb) -> cb.greaterThan(root.get("numRefrendos"), 0));
            // Basta una partida vendida o apartada (RN-17)
            case VENDIDOS -> (root, query, cb) -> cb.and(
                    root.get("estatus").in(EstatusContrato.FINIQUITADO, EstatusContrato.CANCELADO).not(),
                    cb.or(
                            cb.equal(root.get("estatus"), EstatusContrato.VENDIDO),
                            existePartida(root, query, cb, (p, b) ->
                                    p.get("estatus").in(EstatusPartida.VEN, EstatusPartida.APA))));
            case FINIQUITADOS -> (root, query, cb) -> cb.equal(root.get("estatus"), EstatusContrato.FINIQUITADO);
            case CANCELADOS -> (root, query, cb) -> cb.equal(root.get("estatus"), EstatusContrato.CANCELADO);
        };
    }

    /** Sin cerrar y sin partidas vendidas ni apartadas: el contrato todavía se puede cobrar. */
    private static Specification<Contrato> abierto() {
        return (root, query, cb) -> cb.and(
                root.get("estatus").in(CERRADOS).not(),
                sinPartidasVendidasNiApartadas(root, query, cb));
    }

    /** Abierto y sin pase a venta registrado: su estatus lo dictan solo las fechas. */
    private static Specification<Contrato> abiertoPorFechas() {
        return (root, query, cb) -> cb.and(
                root.get("estatus").in(ABIERTOS_POR_FECHA),
                sinPartidasVendidasNiApartadas(root, query, cb));
    }

    private static Predicate sinPartidasVendidasNiApartadas(Root<Contrato> root, CriteriaQuery<?> query,
                                                           CriteriaBuilder cb) {
        return existePartida(root, query, cb,
                (p, b) -> p.get("estatus").in(EstatusPartida.VEN, EstatusPartida.APA)).not();
    }

    private static Predicate existePartida(Root<Contrato> root, CriteriaQuery<?> query, CriteriaBuilder cb,
                                           BiFunction<Root<PartidaContrato>, CriteriaBuilder, Predicate> condicion) {
        Subquery<Long> sub = query.subquery(Long.class);
        Root<PartidaContrato> partida = sub.from(PartidaContrato.class);
        sub.select(partida.get("id"))
                .where(cb.equal(partida.get("contrato").get("id"), root.get("id")), condicion.apply(partida, cb));
        return cb.exists(sub);
    }
}
