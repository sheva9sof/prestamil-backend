package com.ignis.prestamil.service;

import com.ignis.prestamil.model.CatSubtipoPrenda;
import com.ignis.prestamil.repository.CatSubtipoPrendaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CatSubtipoPrendaService extends BaseService<CatSubtipoPrenda, Integer, CatSubtipoPrendaRepository> {

    public CatSubtipoPrendaService(CatSubtipoPrendaRepository repository) {
        super(repository);
    }

    /**
     * Busca todos los subtipos de prenda por tipo de prenda
     *
     * @param idTipoPrenda ID del tipo de prenda
     * @return Lista de subtipos de prenda del tipo especificado
     */
    public List<CatSubtipoPrenda> findByIdTipoPrenda(Integer idTipoPrenda) {
        return repository.findByTipoPrendaIdOrderByIdAtributoAsc(idTipoPrenda);
    }

}

