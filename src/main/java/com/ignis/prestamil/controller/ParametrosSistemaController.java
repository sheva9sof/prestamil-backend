package com.ignis.prestamil.controller;

import com.ignis.prestamil.mapper.ParametrosSistemaMapper;
import com.ignis.prestamil.model.ParametrosSistema;
import com.ignis.prestamil.response.ParametrosSistemaResponse;
import com.ignis.prestamil.service.ParametrosSistemaService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/parametros-sistema")
public class ParametrosSistemaController {

    private final ParametrosSistemaService parametrosSistemaService;
    private final ParametrosSistemaMapper parametrosSistemaMapper;

    public ParametrosSistemaController(ParametrosSistemaService parametrosSistemaService, ParametrosSistemaMapper parametrosSistemaMapper) {
        this.parametrosSistemaService = parametrosSistemaService;
        this.parametrosSistemaMapper = parametrosSistemaMapper;
    }

    @GetMapping
    public List<ParametrosSistemaResponse> findAll() {
        return parametrosSistemaService.findAll().stream()
                .map(parametrosSistemaMapper::toParametrosSistemaResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ParametrosSistemaResponse findById(@PathVariable Integer id) {
        ParametrosSistema parametro = parametrosSistemaService.findById(id);
        return parametrosSistemaMapper.toParametrosSistemaResponse(parametro);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ParametrosSistemaResponse create(@RequestBody ParametrosSistema parametro) {
        ParametrosSistema savedParametro = parametrosSistemaService.save(parametro);
        return parametrosSistemaMapper.toParametrosSistemaResponse(savedParametro);
    }

    @PutMapping("/{id}")
    public ParametrosSistemaResponse update(@PathVariable Integer id, @RequestBody ParametrosSistema parametrosDetails) {
        // Asumo que ParametrosSistemaService tendrá un método update como en UsuarioService
        // Si no lo tiene, este método update genérico de BaseService funcionará si se envía el objeto completo
        parametrosDetails.setId(id);
        ParametrosSistema updatedParametro = parametrosSistemaService.update(parametrosDetails);
        return parametrosSistemaMapper.toParametrosSistemaResponse(updatedParametro);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer id) {
        // Asumo un borrado físico para esta entidad.
        // Necesito restaurar deleteById en BaseService para que esto funcione.
        parametrosSistemaService.deleteById(id);
    }
}
