package com.ignis.prestamil.service;

import com.ignis.prestamil.mapper.PlazoMapper;
import com.ignis.prestamil.model.Plazo;
import com.ignis.prestamil.model.PlazoParametro;
import com.ignis.prestamil.model.TipoPrenda;
import com.ignis.prestamil.repository.PlazoParametroRepository;
import com.ignis.prestamil.repository.PlazoRepository;
import com.ignis.prestamil.request.PlazoRequest;
import com.ignis.prestamil.response.PlazoResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class PlazoService extends BaseService<Plazo, Long, PlazoRepository> {

    private final PlazoMapper plazoMapper;
    private final TipoPrendaService tipoPrendaService;
    private final PlazoParametroRepository plazoParametroRepository;

    public PlazoService(PlazoRepository repository, PlazoMapper plazoMapper, TipoPrendaService tipoPrendaService, PlazoParametroRepository plazoParametroRepository) {
        super(repository);
        this.plazoMapper = plazoMapper;
        this.tipoPrendaService = tipoPrendaService;
        this.plazoParametroRepository = plazoParametroRepository;
    }

    /**
     * Crea un nuevo plazo
     *
     * @param request DTO con los datos del nuevo plazo
     * @return PlazoResponse con los datos del plazo creado
     */
    public PlazoResponse create(PlazoRequest request) {
        // Convertir request a entidad
        Plazo plazo = plazoMapper.toPlazo(request);

        // Asignar tipos de prenda si se proporcionaron
        if (request.getTiposPrenda() != null && !request.getTiposPrenda().isEmpty()) {
            List<TipoPrenda> tiposPrenda = new ArrayList<>();
            for (Integer tipoPrendaId : request.getTiposPrenda()) {
                TipoPrenda tipoPrenda = tipoPrendaService.findById(tipoPrendaId);
                tiposPrenda.add(tipoPrenda);
            }
            plazo.setTiposPrenda(tiposPrenda);
        }

        // Guardar en base de datos (los timestamps se crearán automáticamente)
        Plazo saved = super.save(plazo);

        // Convertir entidad a response y retornar
        return plazoMapper.toPlazoResponse(saved);
    }

    /**
     * Actualiza un plazo existente
     *
     * @param id ID del plazo a actualizar
     * @param request DTO con los datos a actualizar
     * @return PlazoResponse con los datos actualizados
     */
    public PlazoResponse update(Long id, PlazoRequest request) {
        // Buscar el plazo existente (si no existe, lanzará ResourceNotFoundException)
        Plazo plazo = super.findById(id);

        // Actualizar los campos (excepto ID y timestamps)
        plazo.setNombre(request.getNombre());
        plazo.setDiasPorPeriodo(request.getDiasPorPeriodo());
        plazo.setNumeroPeriodos(request.getNumeroPeriodos());
        if (request.getActivo() != null) {
            plazo.setActivo(request.getActivo());
        }

        // Actualizar tipos de prenda si se proporcionaron
        if (request.getTiposPrenda() != null) {
            List<TipoPrenda> tiposPrenda = new ArrayList<>();
            if (!request.getTiposPrenda().isEmpty()) {
                for (Integer tipoPrendaId : request.getTiposPrenda()) {
                    TipoPrenda tipoPrenda = tipoPrendaService.findById(tipoPrendaId);
                    tiposPrenda.add(tipoPrenda);
                }
            }
            plazo.setTiposPrenda(tiposPrenda);
        }
        // actualizadoEn se actualizará automáticamente por @UpdateTimestamp

        // Guardar los cambios
        Plazo updated = super.update(plazo);

        // Convertir entidad a response y retornar
        return plazoMapper.toPlazoResponse(updated);
    }

    /**
     * Obtiene los parámetros de plazo para una combinación específica de plazo y tipo de prenda
     *
     * @param idPlazo ID del plazo
     * @param idTipoPrenda ID del tipo de prenda
     * @return PlazoParametro para la combinación especificada, o null si no existe
     */
    public PlazoParametro getParametrosPlazo(Long idPlazo, Integer idTipoPrenda) {
        return plazoParametroRepository.findByPlazoIdAndTipoPrendaId(idPlazo, idTipoPrenda)
                .orElse(null);
    }

}

