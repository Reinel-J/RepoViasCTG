package com.viactg.controller;

import com.viactg.dto.CambioRolRequest;
import com.viactg.dto.UsuarioActualizacionRequest;
import com.viactg.dto.UsuarioResponse;
import com.viactg.mapper.UsuarioMapper;
import com.viactg.security.UsuarioPrincipal;
import com.viactg.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {
    private final UsuarioService usuarioService;
    private final UsuarioMapper usuarioMapper;
    public UsuarioController(UsuarioService usuarioService, UsuarioMapper usuarioMapper) { this.usuarioService = usuarioService; this.usuarioMapper = usuarioMapper; }

    @GetMapping("/me")
    public UsuarioResponse perfil(@AuthenticationPrincipal UsuarioPrincipal principal) { return usuarioMapper.toResponse(usuarioService.buscarPorId(principal.getId())); }

    @PutMapping("/me")
    public UsuarioResponse actualizarPerfil(@AuthenticationPrincipal UsuarioPrincipal principal, @Valid @RequestBody UsuarioActualizacionRequest request) {
        return usuarioMapper.toResponse(usuarioService.actualizarPerfil(principal.getId(), request));
    }

    @PatchMapping("/{id}/rol")
    @PreAuthorize("hasRole('ADMIN')")
    public UsuarioResponse cambiarRol(@PathVariable String id, @AuthenticationPrincipal UsuarioPrincipal principal, @Valid @RequestBody CambioRolRequest request) {
        return usuarioMapper.toResponse(usuarioService.cambiarRol(id, request.rol(), principal.getId()));
    }

    @PatchMapping("/{id}/activo")
    @PreAuthorize("hasRole('ADMIN')")
    public UsuarioResponse cambiarActivo(@PathVariable String id, @AuthenticationPrincipal UsuarioPrincipal principal, @RequestParam boolean activo) {
        return usuarioMapper.toResponse(usuarioService.cambiarActivo(id, activo, principal.getId()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UsuarioResponse> listar() { return usuarioService.listar().stream().map(usuarioMapper::toResponse).toList(); }
}
