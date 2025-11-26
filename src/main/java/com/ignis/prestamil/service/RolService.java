package com.ignis.prestamil.service;

import com.ignis.prestamil.model.Rol;
import com.ignis.prestamil.repository.RolRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RolService extends BaseService<Rol, Integer, RolRepository> {

    public RolService(RolRepository repository) {
        super(repository);
    }
    
}
