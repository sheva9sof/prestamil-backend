package com.ignis.prestamil.service;

import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.repository.EmpresaRepository;
import com.ignis.prestamil.repository.SucursalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
public class SucursalService extends BaseService<Sucursal, Integer, SucursalRepository> {

    private final EmpresaRepository empresaRepository;

    public SucursalService(SucursalRepository repository, EmpresaRepository empresaRepository) {
        super(repository);
        this.empresaRepository = empresaRepository;
    }

    /**
     * Crea una nueva sucursal, estableciendo la relación con la empresa si se proporciona idRazonSocial
     */
    @Override
    public Sucursal save(Sucursal sucursal) {
        loadEmpresa(sucursal);
        return super.save(sucursal);
    }

    /**
     * Actualiza una sucursal existente, estableciendo la relación con la empresa si se proporciona
     */
    @Override
    public Sucursal update(Sucursal sucursal) {
        loadEmpresa(sucursal);
        return super.update(sucursal);
    }

    /**
     * Obtiene la única sucursal de la base de datos
     * @return Optional con la sucursal si existe
     */
    public Optional<Sucursal> findUnique() {
        return repository.findFirstByOrderByIdAsc();
    }

    /**
     * Carga la empresa si se proporciona el ID en el objeto empresa de la sucursal
     */
    private void loadEmpresa(Sucursal sucursal) {
        if (sucursal.getEmpresa() != null && sucursal.getEmpresa().getId() != null) {
            empresaRepository.findById(sucursal.getEmpresa().getId())
                    .ifPresent(sucursal::setEmpresa);
        }
    }

}

