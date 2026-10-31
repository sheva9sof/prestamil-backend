package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.exception.ConflictException;
import com.ignis.prestamil.exception.ForbiddenException;
import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.model.CajaArranque;
import com.ignis.prestamil.model.Configuracion;
import com.ignis.prestamil.model.FolioSecuencia;
import com.ignis.prestamil.model.FolioSecuenciaId;
import com.ignis.prestamil.model.SerieFolio;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.CajaArranqueRepository;
import com.ignis.prestamil.repository.CierreCajaDiarioRepository;
import com.ignis.prestamil.repository.ConfiguracionRepository;
import com.ignis.prestamil.repository.FolioSecuenciaRepository;
import com.ignis.prestamil.repository.UsuarioRepository;
import com.ignis.prestamil.request.ArranqueCajaRequest;
import com.ignis.prestamil.request.FolioInicialRequest;
import com.ignis.prestamil.response.ArranqueCajaResponse;
import com.ignis.prestamil.util.Constantes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Saldo de arranque de caja (CAJ-06, B-04). Sistemas lo captura una sola vez por sucursal, el día de la
 * migración: la fecha de arranque, el saldo de la bóveda al abrir ese día y el último folio que emitió
 * COCAE en cada serie de {@link FolioService}, para que la numeración siga desde ahí.
 *
 * <p>Hasta esa captura la sucursal puede tener un arranque provisional (el de {@code configuraciones} que
 * pasó el changeset 086): el corte lo usa, pero no se puede cerrar ningún día con él. Una vez confirmado ya
 * no cambia, porque los cierres arrastran su saldo.</p>
 */
@Service
@Transactional
@Slf4j
public class CajaArranqueService {

    private static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DD_MM_YYYY_HH_MM = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final CajaArranqueRepository cajaArranqueRepository;
    private final CierreCajaDiarioRepository cierreCajaDiarioRepository;
    private final FolioSecuenciaRepository folioSecuenciaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ConfiguracionRepository configuracionRepository;
    private final Clock clock;

    public CajaArranqueService(CajaArranqueRepository cajaArranqueRepository,
                               CierreCajaDiarioRepository cierreCajaDiarioRepository,
                               FolioSecuenciaRepository folioSecuenciaRepository,
                               UsuarioRepository usuarioRepository,
                               ConfiguracionRepository configuracionRepository,
                               Clock clock) {
        this.cajaArranqueRepository = cajaArranqueRepository;
        this.cierreCajaDiarioRepository = cierreCajaDiarioRepository;
        this.folioSecuenciaRepository = folioSecuenciaRepository;
        this.usuarioRepository = usuarioRepository;
        this.configuracionRepository = configuracionRepository;
        this.clock = clock;
    }

    /**
     * Arranque de la sucursal con el último folio de cada serie.
     *
     * @param sucursalId sucursal
     * @param username   usuario de la sesión
     * @return el arranque (vacío si la sucursal no tiene), los folios y si el usuario puede capturarlo
     * @throws ResourceNotFoundException si el usuario no existe
     */
    @Transactional(readOnly = true)
    public ArranqueCajaResponse consultar(Integer sucursalId, String username) {
        Usuario usuario = usuario(username);
        Optional<CajaArranque> arranque = cajaArranqueRepository.findById(sucursalId);
        boolean confirmado = arranque.map(a -> Boolean.TRUE.equals(a.getConfirmado())).orElse(false);
        return toResponse(sucursalId, arranque.orElse(null), !confirmado && tieneRol(usuario));
    }

    /**
     * Captura el arranque de la sucursal y los folios iniciales. Una serie que no viene se queda como está.
     *
     * @param sucursalId sucursal
     * @param request    fecha, saldo de la bóveda y último folio de COCAE de cada serie
     * @param username   usuario que captura
     * @return el arranque ya confirmado
     * @throws ForbiddenException  si el rol no puede capturar el arranque
     * @throws ConflictException   si el arranque ya se capturó o ya hay días cerrados
     * @throws BadRequestException si la fecha es futura, una serie viene repetida o un folio es menor al
     *                             último que la serie ya emitió
     */
    public ArranqueCajaResponse capturar(Integer sucursalId, ArranqueCajaRequest request, String username) {
        Usuario usuario = usuario(username);
        if (!tieneRol(usuario)) {
            throw new ForbiddenException("Solo Sistemas puede capturar el saldo de arranque de caja.");
        }

        // 1. Una sola vez por sucursal: la fila bloqueada evita dos capturas simultáneas
        Optional<CajaArranque> actual = cajaArranqueRepository.findByIdParaActualizar(sucursalId);
        if (actual.isPresent() && Boolean.TRUE.equals(actual.get().getConfirmado())) {
            CajaArranque a = actual.get();
            throw new ConflictException("El saldo de arranque ya se capturó ("
                    + (a.getUsuarioCaptura() != null ? a.getUsuarioCaptura().getNombreUsuario() + ", " : "")
                    + (a.getFechaCaptura() != null ? a.getFechaCaptura().format(DD_MM_YYYY_HH_MM) : "")
                    + "): ya no se puede cambiar.");
        }
        if (cierreCajaDiarioRepository.existsBySucursalId(sucursalId)) {
            throw new ConflictException("La sucursal ya tiene días de caja cerrados: el arranque ya no se puede cambiar.");
        }

        // 2. El arranque es el día de la migración: hoy o antes
        LocalDate hoy = LocalDate.now(clock);
        if (request.getFecha().isAfter(hoy)) {
            throw new BadRequestException("La fecha de arranque (" + request.getFecha().format(DD_MM_YYYY)
                    + ") no puede ser posterior a hoy.");
        }

        // 3. Folios iniciales: la numeración sigue desde el último de COCAE, sin repetir uno ya emitido
        Set<SerieFolio> vistas = EnumSet.noneOf(SerieFolio.class);
        for (FolioInicialRequest folio : request.getFolios()) {
            if (!vistas.add(folio.getSerie())) {
                throw new BadRequestException("La serie " + folio.getSerie() + " viene repetida.");
            }
            fijarUltimoFolio(sucursalId, folio.getSerie(), folio.getUltimoFolio());
        }

        // 4. El arranque queda confirmado
        CajaArranque arranque = actual.orElseGet(() -> {
            CajaArranque nuevo = new CajaArranque();
            nuevo.setSucursalId(sucursalId);
            return nuevo;
        });
        arranque.setFechaArranque(request.getFecha());
        arranque.setSaldoBoveda(request.getSaldoBoveda().setScale(2));
        arranque.setConfirmado(true);
        arranque.setUsuarioCaptura(usuario);
        arranque.setFechaCaptura(LocalDateTime.now(clock));
        arranque = cajaArranqueRepository.save(arranque);

        log.info("Arranque de caja sucursal={} fecha={} saldoBoveda={} folios={} usuario={}",
                sucursalId, arranque.getFechaArranque(), arranque.getSaldoBoveda(),
                request.getFolios().stream().map(f -> f.getSerie() + "=" + f.getUltimoFolio()).toList(),
                usuario.getNombreUsuario());
        return toResponse(sucursalId, arranque, false);
    }

    /** Deja el contador de la serie en el último folio de COCAE; el contador nunca baja. */
    private void fijarUltimoFolio(Integer sucursalId, SerieFolio serie, Long ultimoFolio) {
        folioSecuenciaRepository.crearSiNoExiste(sucursalId, serie.name());
        FolioSecuencia contador = folioSecuenciaRepository.findBySucursalIdAndSerie(sucursalId, serie)
                .orElseThrow(() -> new IllegalStateException(
                        "No existe el contador de folios " + serie + " de la sucursal " + sucursalId));
        if (ultimoFolio < contador.getUltimoFolio()) {
            throw new BadRequestException("La serie " + serie + " ya emitió hasta el folio "
                    + contador.getUltimoFolio() + ": el último folio de COCAE no puede ser menor ("
                    + ultimoFolio + ").");
        }
        contador.setUltimoFolio(ultimoFolio);
        folioSecuenciaRepository.save(contador);
    }

    private ArranqueCajaResponse toResponse(Integer sucursalId, CajaArranque a, boolean puedeCapturar) {
        List<ArranqueCajaResponse.Folio> folios = Arrays.stream(SerieFolio.values())
                .map(serie -> new ArranqueCajaResponse.Folio(serie, folioSecuenciaRepository
                        .findById(new FolioSecuenciaId(sucursalId, serie))
                        .map(FolioSecuencia::getUltimoFolio)
                        .orElse(0L)))
                .toList();
        LocalDate hoy = LocalDate.now(clock);
        if (a == null) {
            return new ArranqueCajaResponse(null, null, false, null, null, null, folios, hoy, puedeCapturar);
        }
        return new ArranqueCajaResponse(a.getFechaArranque(), a.getSaldoBoveda(),
                Boolean.TRUE.equals(a.getConfirmado()),
                a.getUsuarioCaptura() != null ? a.getUsuarioCaptura().getNombreUsuario() : null,
                a.getFechaCaptura(), a.getFechaUltimoCierre(), folios, hoy, puedeCapturar);
    }

    private Usuario usuario(String username) {
        return usuarioRepository.findByNombreUsuario(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + username));
    }

    private boolean tieneRol(Usuario usuario) {
        Integer rolId = usuario.getRol() != null ? usuario.getRol().getId() : null;
        return rolId != null && rolesPermitidos().contains(rolId);
    }

    /** CSV de ids de rol de {@code ROLES_PERMITIDOS_ARRANQUE_CAJA}; conjunto vacío = nadie puede. */
    private Set<Integer> rolesPermitidos() {
        return configuracionRepository.findByConfiguracion(Constantes.ROLES_PERMITIDOS_ARRANQUE_CAJA)
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
