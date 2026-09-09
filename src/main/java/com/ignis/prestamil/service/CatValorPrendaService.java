package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.model.CatSubtipoPrenda;
import com.ignis.prestamil.model.CatValorPrenda;
import com.ignis.prestamil.repository.CatSubtipoPrendaRepository;
import com.ignis.prestamil.repository.CatValorPrendaRepository;
import com.ignis.prestamil.request.CatValorPrendaRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CatValorPrendaService extends BaseService<CatValorPrenda, Integer, CatValorPrendaRepository> {

    private final CatSubtipoPrendaRepository catSubtipoPrendaRepository;

    public CatValorPrendaService(CatValorPrendaRepository repository,
                                 CatSubtipoPrendaRepository catSubtipoPrendaRepository) {
        super(repository);
        this.catSubtipoPrendaRepository = catSubtipoPrendaRepository;
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

    public CatValorPrenda create(CatValorPrendaRequest request) {
        CatSubtipoPrenda subtipo = findAndValidateSubtipo(request);
        CatValorPrenda valor = new CatValorPrenda();
        valor.setSubtipoPrenda(subtipo);
        applyEditableFields(valor, request);
        return repository.save(valor);
    }

    public CatValorPrenda update(Integer id, CatValorPrendaRequest request) {
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
        // La descripción ya no se captura en el modal del catálogo: si no viene en el
        // request se conserva la existente (nombres históricos como "AHOGADOR ORO 14K").
        if (request.getDescripcion() != null) {
            valor.setDescripcion(request.getDescripcion().trim());
        }
        valor.setClave(request.getClave() != null ? request.getClave().trim() : null);
        valor.setKilataje(request.getKilataje());
        valor.setContienePiedad(Boolean.TRUE.equals(request.getContienePiedad()));
    }

}

