package com.viactg.controller;

import com.viactg.dto.ComentarioActualizarRequest;
import com.viactg.dto.ComentarioCrearRequest;
import com.viactg.dto.ComentarioResponse;
import com.viactg.mapper.ComentarioMapper;
import com.viactg.security.UsuarioPrincipal;
import com.viactg.service.ComentarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class ComentarioController {
    private final ComentarioService comentarioService;
    private final ComentarioMapper comentarioMapper;
    public ComentarioController(ComentarioService comentarioService, ComentarioMapper comentarioMapper) { this.comentarioService = comentarioService; this.comentarioMapper = comentarioMapper; }
    @GetMapping("/api/reportes/{reporteId}/comentarios")
    public List<ComentarioResponse> listar(@PathVariable String reporteId) { return comentarioService.listarPorReporte(reporteId).stream().map(comentarioMapper::toResponse).toList(); }
    @PostMapping("/api/reportes/{reporteId}/comentarios") @ResponseStatus(HttpStatus.CREATED)
    public ComentarioResponse crear(@PathVariable String reporteId, @AuthenticationPrincipal UsuarioPrincipal principal, @Valid @RequestBody ComentarioCrearRequest request) { return comentarioMapper.toResponse(comentarioService.crear(reporteId, principal.getId(), request)); }
    @PutMapping("/api/comentarios/{id}")
    public ComentarioResponse actualizar(@PathVariable String id, @AuthenticationPrincipal UsuarioPrincipal principal, @Valid @RequestBody ComentarioActualizarRequest request) { return comentarioMapper.toResponse(comentarioService.actualizar(id, principal.getId(), request)); }
    @DeleteMapping("/api/comentarios/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable String id, @AuthenticationPrincipal UsuarioPrincipal principal) { comentarioService.eliminar(id, principal.getId()); }
}
