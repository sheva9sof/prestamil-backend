package com.ignis.prestamil.service;

import com.ignis.prestamil.model.Cliente;
import com.ignis.prestamil.repository.ClienteRepository;
import com.ignis.prestamil.repository.DireccionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * Busca clientes por nombre completo o teléfono
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
        if (cliente.getDireccion() != null && cliente.getDireccion().getId() != null) {
            direccionRepository.findById(cliente.getDireccion().getId())
                    .ifPresent(cliente::setDireccion);
        }
    }

}

