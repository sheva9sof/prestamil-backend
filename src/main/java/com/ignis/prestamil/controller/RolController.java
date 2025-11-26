package com.ignis.prestamil.controller;

import com.ignis.prestamil.mapper.RolMapper;
import com.ignis.prestamil.model.Rol;
import com.ignis.prestamil.response.RolResponse;
import com.ignis.prestamil.service.RolService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/roles")
public class RolController {

    private final RolService rolService;
    private final RolMapper rolMapper;

    public RolController(RolService rolService, RolMapper rolMapper) {
        this.rolService = rolService;
        this.rolMapper = rolMapper;
    }

    @GetMapping
    public List<RolResponse> findAll() {
        List<Rol> roles = rolService.findAll();
        return roles.stream()
                .map(rolMapper::toRolResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public RolResponse findById(@PathVariable Integer id) {
        Rol rol = rolService.findById(id);
        return rolMapper.toRolResponse(rol);
    }
}
