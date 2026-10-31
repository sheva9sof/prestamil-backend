package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.model.Configuracion;
import com.ignis.prestamil.model.Rol;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.ConfiguracionRepository;
import com.ignis.prestamil.repository.UsuarioRepository;
import com.ignis.prestamil.response.CorteCajaResponse;
import com.ignis.prestamil.service.calculo.CorteCajaService;
import com.ignis.prestamil.util.Constantes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Consulta del corte de caja desde la pantalla (CAJ-07a). Las cifras las calcula
 * {@link CorteCajaService}, la fuente única (RC-09); aquí solo se decide qué día puede ver el usuario.
 *
 * <p>Igual que el pase de almoneda (OTR-01): solo los roles de
 * {@code ROLES_PERMITIDOS_CORTE_CAJA_OTRA_FECHA} (Gerente y Sistemas) pueden pedir otra fecha. Para el
 * cajero se ignora la fecha recibida y se usa la de hoy del servidor.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CorteCajaConsultaService {

    private static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CorteCajaService corteCajaService;
    private final UsuarioRepository usuarioRepository;
    private final ConfiguracionRepository configuracionRepository;
    private final Clock clock;

    /**
     * Corte de caja del día que el usuario puede consultar.
     *
     * @param sucursalId      sucursal
     * @param fechaSolicitada fecha del selector de la pantalla (puede ser nula = hoy)
     * @param username        usuario que consulta
     * @return el corte de la fecha efectiva, si el usuario puede cambiarla y la fecha de hoy
     * @throws BadRequestException si quien puede elegir fecha pide un día futuro
     */
    public CorteCajaResponse consultar(Integer sucursalId, LocalDate fechaSolicitada, String username) {
        LocalDate hoy = LocalDate.now(clock);
        boolean puedeElegirFecha = puedeElegirFecha(username);

        // El cajero siempre ve hoy; el resto, el día que pidió
        LocalDate fecha = puedeElegirFecha && fechaSolicitada != null ? fechaSolicitada : hoy;
        if (fecha.isAfter(hoy)) {
            throw new BadRequestException("No hay corte de caja del " + fecha.format(DD_MM_YYYY)
                    + ": es un día futuro.");
        }
        return new CorteCajaResponse(fecha, hoy, puedeElegirFecha, corteCajaService.calcular(sucursalId, fecha));
    }

    /**
     * Indica si el rol del usuario está en {@code ROLES_PERMITIDOS_CORTE_CAJA_OTRA_FECHA}.
     *
     * @param username usuario autenticado
     * @return {@code true} si puede consultar el corte de otra fecha
     */
    public boolean puedeElegirFecha(String username) {
        Integer rolId = usuarioRepository.findByNombreUsuario(username)
                .map(Usuario::getRol)
                .map(Rol::getId)
                .orElse(null);
        return rolId != null && rolesPermitidosOtraFecha().contains(rolId);
    }

    /** CSV de ids de rol de la configuración; conjunto vacío = nadie elige otra fecha. */
    private Set<Integer> rolesPermitidosOtraFecha() {
        return configuracionRepository.findByConfiguracion(Constantes.ROLES_PERMITIDOS_CORTE_CAJA_OTRA_FECHA)
                .map(Configuracion::getValorCadena)
                .filter(csv -> csv != null && !csv.isBlank())
                .map(csv -> Arrays.stream(csv.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(Integer::parseInt)
                        .collect(Collectors.toSet()))
                .orElseGet(Collections::emptySet);
    }
}
