package com.ignis.prestamil.service;

import com.ignis.prestamil.model.Catalogo;
import com.ignis.prestamil.repository.CatalogoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CatalogoService extends BaseService<Catalogo, Integer, CatalogoRepository> {

    public CatalogoService(CatalogoRepository repository) {
        super(repository);
    }

}

