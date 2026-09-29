package com.ignis.prestamil.service;

import com.ignis.prestamil.exception.BadRequestException;
import com.ignis.prestamil.model.Banco;
import com.ignis.prestamil.repository.BancoRepository;
import com.ignis.prestamil.request.BancoRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Catálogo de bancos emisores para pagos con tarjeta (RN-24), administrable desde Parámetros
 * Generales. No hay borrado físico: los movimientos guardan el banco, así que un banco que ya no se
 * usa se desactiva.
 */
@Service
@Transactional
public class BancoService extends BaseService<Banco, Integer, BancoRepository> {

    public BancoService(BancoRepository repository) {
        super(repository);
    }

    /**
     * Lista los bancos por nombre.
     *
     * @param soloActivos true para el combo de la ventana de Cobro; false para la administración
     * @return los bancos ordenados por nombre
     */
    @Transactional(readOnly = true)
    public List<Banco> listar(boolean soloActivos) {
        return soloActivos
                ? repository.findByActivoTrueOrderByNombreAsc()
                : repository.findAllByOrderByNombreAsc();
    }

    /**
     * Da de alta un banco.
     *
     * @param request nombre y estatus
     * @return el banco creado
     * @throws BadRequestException si ya existe un banco con ese nombre
     */
    public Banco crear(BancoRequest request) {
        String nombre = request.getNombre().trim();
        if (repository.existsByNombreIgnoreCase(nombre)) {
            throw new BadRequestException("Ya existe un banco con el nombre " + nombre);
        }
        Banco banco = new Banco();
        banco.setNombre(nombre);
        banco.setActivo(request.getActivo() == null || request.getActivo());
        return repository.save(banco);
    }

    /**
     * Cambia el nombre o el estatus de un banco.
     *
     * @param id      identificador del banco
     * @param request nombre y estatus
     * @return el banco actualizado
     * @throws BadRequestException si otro banco ya tiene ese nombre
     */
    public Banco actualizar(Integer id, BancoRequest request) {
        Banco banco = findById(id);
        String nombre = request.getNombre().trim();
        if (repository.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw new BadRequestException("Ya existe un banco con el nombre " + nombre);
        }
        banco.setNombre(nombre);
        if (request.getActivo() != null) {
            banco.setActivo(request.getActivo());
        }
        return repository.save(banco);
    }
}
