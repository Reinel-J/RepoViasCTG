package com.viactg.controller;

import com.viactg.dto.CategoriaRequest;
import com.viactg.dto.CategoriaResponse;
import com.viactg.mapper.CategoriaMapper;
import com.viactg.security.UsuarioPrincipal;
import com.viactg.service.CategoriaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {
    private final CategoriaService categoriaService;
    private final CategoriaMapper categoriaMapper;
    public CategoriaController(CategoriaService categoriaService, CategoriaMapper categoriaMapper) { this.categoriaService = categoriaService; this.categoriaMapper = categoriaMapper; }
    @GetMapping public List<CategoriaResponse> listar(@RequestParam(defaultValue = "true") boolean soloActivas) { return categoriaService.listar(soloActivas).stream().map(categoriaMapper::toResponse).toList(); }
    @GetMapping("/{id}") public CategoriaResponse obtener(@PathVariable String id) { return categoriaMapper.toResponse(categoriaService.buscarPorId(id)); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')") public CategoriaResponse crear(@AuthenticationPrincipal UsuarioPrincipal principal, @Valid @RequestBody CategoriaRequest request) { return categoriaMapper.toResponse(categoriaService.crear(request, principal.getId())); }
    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") public CategoriaResponse actualizar(@PathVariable String id, @AuthenticationPrincipal UsuarioPrincipal principal, @Valid @RequestBody CategoriaRequest request) { return categoriaMapper.toResponse(categoriaService.actualizar(id, request, principal.getId())); }
    @PatchMapping("/{id}/estado") @PreAuthorize("hasRole('ADMIN')") public CategoriaResponse cambiarEstado(@PathVariable String id, @RequestParam boolean activa, @AuthenticationPrincipal UsuarioPrincipal principal) { return categoriaMapper.toResponse(categoriaService.cambiarEstado(id, activa, principal.getId())); }
}
