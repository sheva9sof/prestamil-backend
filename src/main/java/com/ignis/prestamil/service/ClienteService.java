package com.ignis.prestamil.service;

import com.ignis.prestamil.model.Cliente;
import com.ignis.prestamil.model.Direccion;
import com.ignis.prestamil.repository.ClienteRepository;
import com.ignis.prestamil.repository.DireccionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class ClienteService extends BaseService<Cliente, Integer, ClienteRepository> {

    private final DireccionRepository direccionRepository;

    public ClienteService(ClienteRepository repository, DireccionRepository direccionRepository) {
        super(repository);
        this.direccionRepository = direccionRepository;
    }

    /**
     * Busca clientes activos por nombre completo, teléfono, CURP o RFC.
     * @param searchTerm Término de búsqueda
     * @return Lista de clientes que coinciden con la búsqueda
     */
    public List<Cliente> searchByNombreCompletoOrTelefono(String searchTerm) {
        return repository.searchByNombreCompletoOrTelefono(searchTerm);
    }

    /**
     * Crea un nuevo cliente, estableciendo la relación con la dirección si se proporciona direccion_id
     */
    @Override
    public Cliente save(Cliente cliente) {
        loadDireccion(cliente);
        return super.save(cliente);
    }

    /**
     * Actualiza un cliente existente, estableciendo la relación con la dirección si se proporciona
     */
    @Override
    public Cliente update(Cliente cliente) {
        loadDireccion(cliente);
        return super.update(cliente);
    }

    /**
     * Carga la dirección si se proporciona el ID en el objeto dirección del cliente
     */
    private void loadDireccion(Cliente cliente) {
        Direccion dir = cliente.getDireccion();
        if (dir == null) return;

        if (dir.getId() != null) {
            direccionRepository.findById(dir.getId()).ifPresent(existente -> {
                existente.setTipoDireccion(dir.getTipoDireccion());
                existente.setCalle(dir.getCalle());
                existente.setNumeroExterior(dir.getNumeroExterior());
                existente.setNumeroInterior(dir.getNumeroInterior());
                existente.setColonia(dir.getColonia());
                existente.setCiudad(dir.getCiudad());
                existente.setEstado(dir.getEstado());
                existente.setCodigoPostal(dir.getCodigoPostal());
                existente.setReferencias(dir.getReferencias());
                existente.setEsVerificada(dir.getEsVerificada());
                cliente.setDireccion(direccionRepository.save(existente));
            });
        } else {
            if (dir.getFechaRegistro() == null) dir.setFechaRegistro(LocalDate.now());
            cliente.setDireccion(direccionRepository.save(dir));
        }
    }

}

