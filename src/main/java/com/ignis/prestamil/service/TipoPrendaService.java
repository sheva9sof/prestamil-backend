package com.ignis.prestamil.service;

import com.ignis.prestamil.model.TipoPrenda;
import com.ignis.prestamil.repository.TipoPrendaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class TipoPrendaService extends BaseService<TipoPrenda, Integer, TipoPrendaRepository> {

    public TipoPrendaService(TipoPrendaRepository repository) {
        super(repository);
    }

    /**
     * Obtiene todos los tipos de prenda ordenados por ID
     *
     * @return Lista de tipos de prenda
     */
    public List<TipoPrenda> findAllOrdered() {
        return repository.findAllByOrderByIdAsc();
    }

}

