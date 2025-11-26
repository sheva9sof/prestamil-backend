package com.ignis.prestamil.controller;

import com.ignis.prestamil.mapper.ClienteMapper;
import com.ignis.prestamil.model.Cliente;
import com.ignis.prestamil.response.ClienteResponse;
import com.ignis.prestamil.service.ClienteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteService clienteService;
    private final ClienteMapper clienteMapper;

    public ClienteController(ClienteService clienteService, ClienteMapper clienteMapper) {
        this.clienteService = clienteService;
        this.clienteMapper = clienteMapper;
    }

    @GetMapping
    public List<ClienteResponse> findAll() {
        return clienteService.findAll().stream()
                .map(clienteMapper::toClienteResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/search")
    public ResponseEntity<List<ClienteResponse>> search(@RequestParam String q) {
        List<Cliente> clientes = clienteService.searchByNombreCompletoOrTelefono(q);
        List<ClienteResponse> responses = clientes.stream()
                .map(clienteMapper::toClienteResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ClienteResponse findById(@PathVariable Integer id) {
        Cliente cliente = clienteService.findById(id);
        return clienteMapper.toClienteResponse(cliente);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClienteResponse create(@RequestBody Cliente cliente) {
        Cliente savedCliente = clienteService.save(cliente);
        return clienteMapper.toClienteResponse(savedCliente);
    }

    @PutMapping("/{id}")
    public ClienteResponse update(@PathVariable Integer id, @RequestBody Cliente clienteDetails) {
        clienteDetails.setId(id);
        Cliente updatedCliente = clienteService.update(clienteDetails);
        return clienteMapper.toClienteResponse(updatedCliente);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer id) {
        clienteService.deleteById(id);
    }
}
