package com.ignis.prestamil.service;

import com.ignis.prestamil.model.CatValorPrenda;
import com.ignis.prestamil.repository.CatValorPrendaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CatValorPrendaService extends BaseService<CatValorPrenda, Integer, CatValorPrendaRepository> {

    public CatValorPrendaService(CatValorPrendaRepository repository) {
        super(repository);
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

}

