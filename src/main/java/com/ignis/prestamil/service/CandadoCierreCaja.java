package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.repository.CajaArranqueRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Un día cerrado queda congelado (CAJ-06): después del cierre no se registra ni se cancela nada con esa
 * fecha. Lo llaman los dos únicos puntos de escritura de caja, {@link MovimientoCajaService#registrar}
 * (empeños, cobros, reposiciones, cancelaciones y ventas) y {@link TraspasoCajaService}, así los servicios
 * de contratos y de ventas no tienen que validarlo por su cuenta.
 *
 * <p>Lee el último día cerrado con bloqueo de la fila de {@code caja_arranque}, la misma que bloquea el
 * cierre: una operación que llega mientras se cierra el día espera al cierre y luego se rechaza, y un
 * cierre que llega durante una operación espera a que termine y la incluye en el corte.</p>
 */
@Component
public class CandadoCierreCaja {

    private static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CajaArranqueRepository cajaArranqueRepository;

    public CandadoCierreCaja(CajaArranqueRepository cajaArranqueRepository) {
        this.cajaArranqueRepository = cajaArranqueRepository;
    }

    /**
     * Rechaza una operación de caja con fecha de un día ya cerrado. La fila queda bloqueada hasta el fin de
     * la transacción de la operación.
     *
     * @param sucursalId sucursal de la operación
     * @param fecha      día al que pertenece la operación (el de hoy del servidor)
     * @throws BadRequestException si ese día ya se cerró
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void validarDiaAbierto(Integer sucursalId, LocalDate fecha) {
        LocalDate ultimoCierre = ultimoCierre(cajaArranqueRepository.ultimoCierreBloqueado(sucursalId));
        if (ultimoCierre != null && !fecha.isAfter(ultimoCierre)) {
            throw new BadRequestException("La caja del " + fecha.format(DD_MM_YYYY) + " ya se cerró: no se "
                    + "pueden registrar ni cancelar operaciones con esa fecha.");
        }
    }

    /** El driver devuelve la fecha como {@code java.sql.Date} o como {@code LocalDate}. */
    private static LocalDate ultimoCierre(List<Object> fila) {
        Object valor = fila.isEmpty() ? null : fila.get(0);
        if (valor instanceof java.sql.Date fecha) {
            return fecha.toLocalDate();
        }
        return (LocalDate) valor;
    }
}
