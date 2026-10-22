package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.exception.ForbiddenException;
import com.ignis.prestamil.exception.ResourceNotFoundException;
import com.ignis.prestamil.model.CatSubtipoPrenda;
import com.ignis.prestamil.model.CatValorPrenda;
import com.ignis.prestamil.model.Configuracion;
import com.ignis.prestamil.model.Usuario;
import com.ignis.prestamil.repository.CatSubtipoPrendaRepository;
import com.ignis.prestamil.repository.CatValorPrendaRepository;
import com.ignis.prestamil.repository.ConfiguracionRepository;
import com.ignis.prestamil.repository.PartidaContratoRepository;
import com.ignis.prestamil.repository.UsuarioRepository;
import com.ignis.prestamil.request.CatValorPrendaRequest;
import com.ignis.prestamil.util.Constantes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class CatValorPrendaService extends BaseService<CatValorPrenda, Integer, CatValorPrendaRepository> {

    private final CatSubtipoPrendaRepository catSubtipoPrendaRepository;
    private final PartidaContratoRepository partidaContratoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ConfiguracionRepository configuracionRepository;

    public CatValorPrendaService(CatValorPrendaRepository repository,
                                 CatSubtipoPrendaRepository catSubtipoPrendaRepository,
                                 PartidaContratoRepository partidaContratoRepository,
                                 UsuarioRepository usuarioRepository,
                                 ConfiguracionRepository configuracionRepository) {
        super(repository);
        this.catSubtipoPrendaRepository = catSubtipoPrendaRepository;
        this.partidaContratoRepository = partidaContratoRepository;
        this.usuarioRepository = usuarioRepository;
        this.configuracionRepository = configuracionRepository;
    }

    /**
     * Busca todos los valores de prenda por subtipo de prenda
     *
     * @param idAtributo ID del atributo (subtipo de prenda)
     * @return Lista de valores de prenda del subtipo especificado
     */
    public List<CatValorPrenda> findByIdAtributo(Integer idAtributo) {
        return repository.findWithSubtipoAndTipoBySubtipoPrendaIdAtributoOrderByIdValorAtributoAsc(idAtributo);
    }

    /**
     * Lista el catálogo completo con el subtipo y tipo inicializados para la respuesta.
     */
    @Transactional(readOnly = true)
    public List<CatValorPrenda> findAllOrdered() {
        return repository.findAllByOrderByIdValorAtributoAsc();
    }

    /**
     * Da de alta un valor del catálogo de prendas. Solo los roles de
     * {@code ROLES_PERMITIDOS_CATALOGO_PRENDAS} (por defecto Sistemas) pueden hacerlo (D-09).
     *
     * @param request  datos del valor; clave y descripción general obligatorias
     * @param username usuario que hace el alta
     * @return el valor guardado
     * @throws ForbiddenException  si el rol del usuario no administra el catálogo
     * @throws BadRequestException si falta la clave o la descripción general
     */
    public CatValorPrenda create(CatValorPrendaRequest request, String username) {
        validarPuedeAdministrarCatalogo(username);
        CatSubtipoPrenda subtipo = findAndValidateSubtipo(request);
        CatValorPrenda valor = new CatValorPrenda();
        valor.setSubtipoPrenda(subtipo);
        applyEditableFields(valor, request);
        return repository.save(valor);
    }

    /**
     * Actualiza un valor del catálogo. Mismas reglas de rol que el alta; al guardarse con
     * clave y descripción definitivas deja de estar pendiente de revisión.
     *
     * @param id       id del valor
     * @param request  datos editables
     * @param username usuario que edita
     * @return el valor actualizado
     * @throws ForbiddenException  si el rol del usuario no administra el catálogo
     * @throws BadRequestException si falta la clave o la descripción general, o se intenta cambiar la categoría
     */
    public CatValorPrenda update(Integer id, CatValorPrendaRequest request, String username) {
        validarPuedeAdministrarCatalogo(username);
        CatValorPrenda valor = findById(id);
        CatSubtipoPrenda subtipo = findAndValidateSubtipo(request);

        if (!valor.getSubtipoPrenda().getIdAtributo().equals(subtipo.getIdAtributo())) {
            throw new BadRequestException("La categoría de una prenda existente no se puede cambiar");
        }

        // Mantiene inicializadas las relaciones necesarias para construir la respuesta fuera del servicio.
        valor.getSubtipoPrenda().getTipoPrenda().getId();
        applyEditableFields(valor, request);
        return repository.save(valor);
    }

    /**
     * Elimina físicamente un valor del catálogo de prendas.
     *
     * @param id       ID del valor a eliminar
     * @param username usuario que elimina
     * @throws ForbiddenException  si el rol del usuario no administra el catálogo
     * @throws BadRequestException si el valor ya fue usado en alguna partida de contrato,
     *                             porque la FK de partida_contrato lo referencia y borrarlo
     *                             dejaría contratos históricos inconsistentes
     */
    public void deleteValor(Integer id, String username) {
        validarPuedeAdministrarCatalogo(username);
        CatValorPrenda valor = findById(id);

        long partidas = partidaContratoRepository.countByValorPrendaIdValorAtributo(id);
        if (partidas > 0) {
            throw new BadRequestException(
                    "No se puede eliminar: la prenda está usada en " + partidas
                            + (partidas == 1 ? " partida de contrato" : " partidas de contrato"));
        }

        repository.delete(valor);
    }

    private CatSubtipoPrenda findAndValidateSubtipo(CatValorPrendaRequest request) {
        CatSubtipoPrenda subtipo = catSubtipoPrendaRepository.findById(request.getIdAtributo())
                .orElseThrow(() -> new BadRequestException(
                        "La categoría de prenda seleccionada no existe: " + request.getIdAtributo()));

        if (subtipo.getTipoPrenda() == null
                || !request.getIdTipoPrenda().equals(subtipo.getTipoPrenda().getId())) {
            throw new BadRequestException("La categoría seleccionada no pertenece al tipo de prenda indicado");
        }
        return subtipo;
    }

    private void applyEditableFields(CatValorPrenda valor, CatValorPrendaRequest request) {
        // D-09: la descripción general vuelve a ser obligatoria (revierte el changeset 024).
        if (request.getDescripcion() == null || request.getDescripcion().isBlank()) {
            throw new BadRequestException("La descripción general de la prenda es obligatoria");
        }
        valor.setDescripcion(request.getDescripcion().trim());
        valor.setClave(normalizarClave(request.getClave()));
        valor.setKilataje(request.getKilataje());
        valor.setContienePiedra(Boolean.TRUE.equals(request.getContienePiedra()));
        // Sistemas ya capturó clave y descripción definitivas: deja de estar pendiente.
        valor.setPendienteRevision(false);
    }

    private static String normalizarClave(String clave) {
        if (clave == null || clave.isBlank()) {
            throw new BadRequestException("La clave de la prenda es obligatoria");
        }
        return clave.trim();
    }

    /** Verifica que el rol del usuario esté en {@code ROLES_PERMITIDOS_CATALOGO_PRENDAS}. */
    private void validarPuedeAdministrarCatalogo(String username) {
        Usuario usuario = usuarioRepository.findByNombreUsuario(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + username));
        Integer rolId = usuario.getRol() != null ? usuario.getRol().getId() : null;
        if (rolId == null || !rolesPermitidos().contains(rolId)) {
            throw new ForbiddenException(
                    "Solo Sistemas puede dar de alta, modificar o eliminar prendas del catálogo");
        }
    }

    /** CSV de ids de rol de la configuración; conjunto vacío = nadie administra el catálogo. */
    private Set<Integer> rolesPermitidos() {
        return configuracionRepository.findByConfiguracion(Constantes.ROLES_PERMITIDOS_CATALOGO_PRENDAS)
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

