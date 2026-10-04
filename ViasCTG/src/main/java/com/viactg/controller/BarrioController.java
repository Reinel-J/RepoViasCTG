package com.viactg.controller;

import com.viactg.dto.BarrioRequest;
import com.viactg.dto.BarrioResponse;
import com.viactg.dto.CalleRequest;
import com.viactg.mapper.BarrioMapper;
import com.viactg.service.BarrioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/barrios")
public class BarrioController {
    private final BarrioService barrioService;
    private final BarrioMapper barrioMapper;
    public BarrioController(BarrioService barrioService, BarrioMapper barrioMapper) { this.barrioService = barrioService; this.barrioMapper = barrioMapper; }

    @GetMapping public List<BarrioResponse> listar() { return barrioService.listar().stream().map(barrioMapper::toResponse).toList(); }
    @GetMapping("/{id}") public BarrioResponse obtener(@PathVariable String id) { return barrioMapper.toResponse(barrioService.buscarPorId(id)); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')")
    public BarrioResponse crear(@Valid @RequestBody BarrioRequest request) { return barrioMapper.toResponse(barrioService.crear(request)); }
    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    public BarrioResponse actualizar(@PathVariable String id, @Valid @RequestBody BarrioRequest request) { return barrioMapper.toResponse(barrioService.actualizar(id, request)); }
    @PostMapping("/{id}/calles") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')")
    public BarrioResponse agregarCalle(@PathVariable String id, @Valid @RequestBody CalleRequest request) { return barrioMapper.toResponse(barrioService.agregarCalle(id, request)); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN')")
    public void eliminar(@PathVariable String id) { barrioService.eliminar(id); }
}
