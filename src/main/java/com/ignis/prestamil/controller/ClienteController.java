package com.ignis.prestamil.controller;

import com.ignis.prestamil.mapper.ClienteMapper;
import com.ignis.prestamil.model.Cliente;
import com.ignis.prestamil.response.ClienteResponse;
import com.ignis.prestamil.service.ClienteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
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
    public ResponseEntity<List<ClienteResponse>> findAll() {
        List<Cliente> clientes = clienteService.findAll();
        List<ClienteResponse> responses = clientes.stream()
                .map(clienteMapper::toClienteResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
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
    public ResponseEntity<ClienteResponse> findById(@PathVariable Integer id) {
        Optional<Cliente> cliente = clienteService.findById(id);
        return cliente.map(c -> ResponseEntity.ok(clienteMapper.toClienteResponse(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ClienteResponse> create(@RequestBody Cliente cliente) {
        Cliente savedCliente = clienteService.save(cliente);
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteMapper.toClienteResponse(savedCliente));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClienteResponse> update(@PathVariable Integer id, @RequestBody Cliente cliente) {
        if (!clienteService.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        cliente.setId(id);
        Cliente updatedCliente = clienteService.update(cliente);
        return ResponseEntity.ok(clienteMapper.toClienteResponse(updatedCliente));
    }

}

