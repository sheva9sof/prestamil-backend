package com.ignis.prestamil.service;

import com.ignis.prestamil.model.Empresa;
import com.ignis.prestamil.repository.EmpresaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EmpresaService extends BaseService<Empresa, Integer, EmpresaRepository> {

    public EmpresaService(EmpresaRepository repository) {
        super(repository);
    }

}

