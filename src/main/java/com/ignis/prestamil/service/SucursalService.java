package com.ignis.prestamil.service;

import com.ignis.prestamil.mapper.SucursalMapper;
import com.ignis.prestamil.model.Sucursal;
import com.ignis.prestamil.repository.EmpresaRepository;
import com.ignis.prestamil.repository.SucursalRepository;
import com.ignis.prestamil.request.SucursalRequest;
import com.ignis.prestamil.response.SucursalResponse;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
public class SucursalService extends BaseService<Sucursal, Integer, SucursalRepository> {

    private final EmpresaRepository empresaRepository;
    private final SucursalMapper sucursalMapper;

    public SucursalService(SucursalRepository repository, EmpresaRepository empresaRepository, SucursalMapper sucursalMapper) {
        super(repository);
        this.empresaRepository = empresaRepository;
        this.sucursalMapper = sucursalMapper;
    }

    /**
     * Crea una nueva sucursal
     * Nota: No se usan nuevas sucursales en este sistema
     */
    @Override
    @CacheEvict(value = "sucursal", allEntries = true)
    public Sucursal save(Sucursal sucursal) {
        return super.save(sucursal);
    }

    /**
     * Actualiza una sucursal existente, estableciendo la relación con la empresa si se proporciona idRazonSocial
     * Limpia el cache y luego lo refresca automáticamente con el nuevo valor después de la actualización
     * 
     * @param id ID de la sucursal a actualizar
     * @param request DTO con los datos a actualizar
     * @return SucursalResponse con los datos actualizados
     */
    @CacheEvict(value = "sucursal", key = "'unique'")
    public SucursalResponse update(Integer id, SucursalRequest request) {
        // Convertir request a entidad
        Sucursal sucursal = sucursalMapper.toSucursal(request);
        sucursal.setId(id);
        
        // Cargar empresa desde idRazonSocial
        loadEmpresa(sucursal, request.getIdRazonSocial());
        
        // Actualizar en base de datos
        Sucursal updated = super.update(sucursal);
        
        // Refrescar el cache con el valor actualizado
        refreshCache();
        
        // Convertir entidad a response y retornar
        return sucursalMapper.toSucursalResponse(updated);
    }

    /**
     * Refresca el cache con la sucursal actualizada desde la base de datos
     * Este método se llama internamente después de un update para actualizar el cache
     */
    @CachePut(value = "sucursal", key = "'unique'")
    public Optional<Sucursal> refreshCache() {
        return repository.findById(1);
    }

    /**
     * Obtiene la única sucursal de la base de datos (siempre ID = 1)
     * El resultado se cachea localmente para mejorar el rendimiento
     * @return Optional con la sucursal si existe
     */
    @Cacheable(value = "sucursal", key = "'unique'")
    public Optional<Sucursal> findUnique() {
        return repository.findById(1);
    }

    /**
     * Carga la empresa desde el idRazonSocial proporcionado en el request
     */
    private void loadEmpresa(Sucursal sucursal, Integer idRazonSocial) {
        if (idRazonSocial != null) {
            empresaRepository.findById(idRazonSocial)
                    .ifPresent(sucursal::setEmpresa);
        }
    }

}

