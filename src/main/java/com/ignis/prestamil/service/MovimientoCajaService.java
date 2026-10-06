package com.ignis.prestamil.service;

import com.ignis.prestamil.model.MovimientoCaja;
import com.ignis.prestamil.model.MovimientoContrato;
import com.ignis.prestamil.model.TipoMovimientoCaja;
import com.ignis.prestamil.model.Turno;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.MovimientoCajaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Registra entradas y salidas de efectivo en caja (C-07). Pensado como base del corte de caja:
 * cada ENTRADA/SALIDA queda vinculada al turno, la sucursal y, cuando aplica, al movimiento de
 * contrato que la originó, para poder reconciliar efectivo al cierre.
 *
 * <p>Primera incorporación (C-06): al cancelar un empeño del día el cliente devuelve el préstamo
 * en efectivo, así que se registra una ENTRADA por ese monto. Los cobros ordinarios y las salidas
 * por devolución de pago con tarjeta se integrarán en C-07.</p>
 */
@Service
@Transactional
@Slf4j
public class MovimientoCajaService {

    private final MovimientoCajaRepository repository;
    private final Clock clock;

    public MovimientoCajaService(MovimientoCajaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Registra una entrada o salida de efectivo en caja. Deja huella del turno, sucursal, usuario
     * y, cuando aplica, el movimiento de contrato que la originó para auditoría cruzada.
     *
     * @param turno              turno en el que se registra el flujo (nunca null)
     * @param sucursalId         sucursal del flujo (nunca null)
     * @param tipo               ENTRADA o SALIDA
     * @param concepto           descripción corta para corte y auditoría (ej. "CANCELACIÓN DE CONTRATO 1493")
     * @param monto              importe en pesos; debe ser > 0
     * @param usuario            usuario que registra el flujo (nunca null)
     * @param movimientoContrato movimiento de contrato que originó este flujo; puede ser null
     * @return el movimiento de caja persistido
     */
    public MovimientoCaja registrar(Turno turno, Integer sucursalId, TipoMovimientoCaja tipo,
                                    String concepto, BigDecimal monto, Usuario usuario,
                                    MovimientoContrato movimientoContrato) {
        MovimientoCaja m = new MovimientoCaja();
        m.setTurno(turno);
        m.setSucursalId(sucursalId);
        m.setTipo(tipo);
        m.setConcepto(concepto);
        m.setMonto(monto);
        m.setUsuario(usuario);
        m.setMovimientoContrato(movimientoContrato);
        m.setFecha(LocalDateTime.now(clock));
        MovimientoCaja guardado = repository.save(m);
        log.info("Caja {} sucursal={} turno={} usuario={} concepto={} monto={}",
                tipo, sucursalId, turno != null ? turno.getId() : null,
                usuario != null ? usuario.getNombreUsuario() : null, concepto, monto);
        return guardado;
    }
}
