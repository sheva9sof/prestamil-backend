package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.model.Banco;
import com.ignis.prestamil.model.MovimientoContrato;
import com.ignis.prestamil.repository.BancoRepository;
import com.ignis.prestamil.request.PagoRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Validación de la ventana de Cobro (RN-24), común a todos los movimientos con importe: refrendos,
 * finiquitos, abonos, parciales y reposición. El servidor valida lo mismo que el frontend; el frontend
 * solo evita el viaje. Corre dentro de la transacción del movimiento que se está registrando.
 */
@Service
public class CobroService {

    private final BancoRepository bancoRepository;

    public CobroService(BancoRepository bancoRepository) {
        this.bancoRepository = bancoRepository;
    }

    /**
     * Valida el pago contra el total y lo copia al movimiento, con el cambio a entregar.
     * En {@code importeEfectivo} queda el efectivo recibido; el efectivo que entra a caja es
     * {@code importeEfectivo - cambioEntregado}.
     *
     * @param mov   movimiento al que se aplica el pago
     * @param pago  forma de pago capturada
     * @param total total a cobrar, ya recalculado por el servidor
     * @throws BadRequestException si el pago no cubre el total, la tarjeta excede el total o faltan datos de la tarjeta
     */
    public void aplicarPago(MovimientoContrato mov, PagoRequest pago, BigDecimal total) {
        BigDecimal efectivo = cero(pago.getEfectivo());
        BigDecimal tarjeta = cero(pago.getTarjeta());

        // El cambio solo puede salir del efectivo: la tarjeta nunca cubre más que el total
        if (tarjeta.compareTo(total) > 0) {
            throw new BadRequestException("El importe con tarjeta ($" + tarjeta
                    + ") no puede ser mayor al total a cobrar ($" + total + ")");
        }
        BigDecimal recibido = efectivo.add(tarjeta);
        if (recibido.compareTo(total) < 0) {
            throw new BadRequestException("El pago ($" + recibido
                    + ") no cubre el total a cobrar ($" + total + ")");
        }

        mov.setImporteEfectivo(efectivo);
        mov.setImporteTarjeta(tarjeta);
        mov.setCambioEntregado(recibido.subtract(total));
        if (tarjeta.signum() > 0) {
            aplicarTarjeta(mov, pago);
        }
    }

    private void aplicarTarjeta(MovimientoContrato mov, PagoRequest pago) {
        if (pago.getTipoTarjeta() == null) {
            throw new BadRequestException("Indique si la tarjeta es de crédito o de débito");
        }
        if (pago.getTarjetaUltimos4() == null || !pago.getTarjetaUltimos4().matches("\\d{4}")) {
            throw new BadRequestException("Capture los últimos 4 dígitos de la tarjeta");
        }
        if (pago.getAutorizacion() == null || pago.getAutorizacion().isBlank()) {
            throw new BadRequestException("Capture el número de autorización del pago con tarjeta");
        }
        if (pago.getBancoEmisorId() == null) {
            throw new BadRequestException("Seleccione el banco emisor de la tarjeta");
        }
        Banco banco = bancoRepository.findById(pago.getBancoEmisorId())
                .filter(Banco::getActivo)
                .orElseThrow(() -> new BadRequestException("El banco emisor seleccionado no existe o está inactivo"));

        mov.setTipoTarjeta(pago.getTipoTarjeta());
        mov.setTarjetaUltimos4(pago.getTarjetaUltimos4());
        mov.setAutorizacionBanco(pago.getAutorizacion().trim());
        mov.setBancoEmisor(banco);
    }

    private static BigDecimal cero(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }
}
