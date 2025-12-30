package com.ignis.prestamil.service;

import com.ignis.prestamil.model.Catalogo;
import com.ignis.prestamil.repository.CatalogoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CatalogoService extends BaseService<Catalogo, Integer, CatalogoRepository> {

    public CatalogoService(CatalogoRepository repository) {
        super(repository);
    }

    /**
     * Busca todos los catálogos por tipo de catálogo
     *
     * @param idTipoCatalogo ID del tipo de catálogo
     * @return Lista de catálogos del tipo especificado
     */
    public List<Catalogo> findByIdTipoCatalogo(Integer idTipoCatalogo) {
        return repository.findByIdTipoCatalogo(idTipoCatalogo);
    }

}

