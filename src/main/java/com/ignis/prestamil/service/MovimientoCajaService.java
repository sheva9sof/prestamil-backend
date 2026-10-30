package com.ignis.prestamil.service;

import com.ignis.prestamil.model.MovimientoCaja;
import com.ignis.prestamil.model.OrigenCaja;
import com.ignis.prestamil.repository.MovimientoCajaRepository;
import com.ignis.prestamil.repository.MovimientoContratoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Único punto de escritura de {@code movimiento_caja} (C-07, CAJ-01b). Base del corte de caja: cada
 * ENTRADA/SALIDA queda con su turno, sucursal, {@link com.ignis.prestamil.model.ConceptoCaja} (renglón
 * del corte), importes separados en efectivo y tarjeta, y el registro que la originó.
 *
 * <p>Lo llaman el empeño ({@code ContratoService}), los cobros, la reposición y las cancelaciones
 * ({@code MovimientoContratoService}) y, desde el módulo de Ventas, las ventas y sus cancelaciones
 * (A-03). Ver el ejemplo de llamada en {@link RegistroCaja}.</p>
 */
@Service
@Transactional
@Slf4j
public class MovimientoCajaService {

    private static final int MAX_DESCRIPCION = 120;

    private final MovimientoCajaRepository repository;
    private final MovimientoContratoRepository movimientoContratoRepository;
    private final Clock clock;

    public MovimientoCajaService(MovimientoCajaRepository repository,
                                 MovimientoContratoRepository movimientoContratoRepository,
                                 Clock clock) {
        this.repository = repository;
        this.movimientoContratoRepository = movimientoContratoRepository;
        this.clock = clock;
    }

    /**
     * Registra una entrada o salida de caja. La dirección sale del concepto; {@code monto} guarda el
     * total (efectivo + tarjeta). Si el origen es un movimiento de contrato también se liga por FK
     * ({@code id_movimiento_contrato}) para la auditoría cruzada.
     *
     * @param registro turno, sucursal, concepto, importes, origen, usuario y descripción del flujo
     * @return el movimiento de caja persistido
     * @throws IllegalArgumentException si el origen viene incompleto
     */
    public MovimientoCaja registrar(RegistroCaja registro) {
        if ((registro.origenTipo() == null) != (registro.origenId() == null)) {
            throw new IllegalArgumentException("El origen del flujo de caja debe traer tipo e id: "
                    + registro.origenTipo() + "/" + registro.origenId());
        }

        MovimientoCaja m = new MovimientoCaja();
        m.setTurno(registro.turno());
        m.setSucursalId(registro.sucursalId());
        m.setTipo(registro.concepto().tipo());
        m.setConceptoCaja(registro.concepto());
        m.setConcepto(recortar(registro.descripcion()));
        m.setMonto(registro.total());
        m.setImporteEfectivo(registro.importeEfectivo());
        m.setImporteTarjeta(registro.importeTarjeta());
        m.setOrigenTipo(registro.origenTipo());
        m.setOrigenId(registro.origenId());
        // El movimiento de contrato ya está en la transacción: la referencia no consulta la BD
        if (registro.origenTipo() == OrigenCaja.MOVIMIENTO_CONTRATO) {
            m.setMovimientoContrato(movimientoContratoRepository.getReferenceById(registro.origenId()));
        }
        m.setUsuario(registro.usuario());
        m.setFecha(LocalDateTime.now(clock));
        MovimientoCaja guardado = repository.save(m);
        log.info("Caja {} {} sucursal={} turno={} usuario={} efectivo={} tarjeta={} origen={}:{} concepto={}",
                m.getTipo(), registro.concepto(), registro.sucursalId(), registro.turno().getId(),
                registro.usuario().getNombreUsuario(), registro.importeEfectivo(), registro.importeTarjeta(),
                registro.origenTipo(), registro.origenId(), m.getConcepto());
        return guardado;
    }

    private static String recortar(String descripcion) {
        String d = descripcion.trim();
        return d.length() > MAX_DESCRIPCION ? d.substring(0, MAX_DESCRIPCION) : d;
    }
}
