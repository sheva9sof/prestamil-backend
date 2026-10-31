package com.ignis.prestamil.service.calculo;

import com.ignis.prestamil.model.Configuracion;
import com.ignis.prestamil.repository.ConfiguracionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Saldo de arranque temporal (CAJ-05): lo leen dos renglones de {@code configuraciones} sembrados por el
 * changeset 084, {@code CAJA_FECHA_ARRANQUE} (yyyy-MM-dd) y {@code CAJA_SALDO_ARRANQUE} (decimal), los dos
 * en {@code valorCadena}. Son de toda la instalación porque hoy hay una sola sucursal (H-07).
 *
 * <p>TODO CAJ-06: se reemplaza por la captura de Sistemas por sucursal (tabla propia).</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SaldoArranqueConfiguracionProvider implements SaldoArranqueProvider {

    static final String CAJA_FECHA_ARRANQUE = "CAJA_FECHA_ARRANQUE";
    static final String CAJA_SALDO_ARRANQUE = "CAJA_SALDO_ARRANQUE";

    private final ConfiguracionRepository configuracionRepository;

    /**
     * Saldo de arranque de la instalación; la sucursal se ignora mientras haya una sola.
     *
     * @param sucursalId identificador de la sucursal
     * @return fecha y saldo de arranque, o vacío si falta alguno de los dos o no se puede leer
     */
    @Override
    public Optional<SaldoArranque> saldoArranque(Integer sucursalId) {
        Optional<String> fecha = valor(CAJA_FECHA_ARRANQUE);
        Optional<String> saldo = valor(CAJA_SALDO_ARRANQUE);
        if (fecha.isEmpty() || saldo.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new SaldoArranque(LocalDate.parse(fecha.get()), new BigDecimal(saldo.get())));
        } catch (DateTimeParseException | NumberFormatException e) {
            // Un valor mal capturado deja el corte sin cifras (con aviso) en vez de inventar un saldo
            log.error("Saldo de arranque de caja ilegible: {}='{}', {}='{}'",
                    CAJA_FECHA_ARRANQUE, fecha.get(), CAJA_SALDO_ARRANQUE, saldo.get());
            return Optional.empty();
        }
    }

    private Optional<String> valor(String configuracion) {
        return configuracionRepository.findByConfiguracion(configuracion)
                .map(Configuracion::getValorCadena)
                .map(String::trim)
                .filter(v -> !v.isEmpty());
    }
}
