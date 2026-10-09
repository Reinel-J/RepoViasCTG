package com.viactg.controller;

import com.viactg.dto.ComentarioActualizarRequest;
import com.viactg.dto.ComentarioCrearRequest;
import com.viactg.dto.ComentarioResponse;
import com.viactg.mapper.ComentarioMapper;
import com.viactg.model.Comentario;
import com.viactg.security.UsuarioPrincipal;
import com.viactg.service.ComentarioService;
import com.viactg.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class ComentarioController {
    private final ComentarioService comentarioService;
    private final ComentarioMapper comentarioMapper;
    private final UsuarioService usuarioService;
    public ComentarioController(ComentarioService comentarioService, ComentarioMapper comentarioMapper, UsuarioService usuarioService) { this.comentarioService = comentarioService; this.comentarioMapper = comentarioMapper; this.usuarioService = usuarioService; }
    @GetMapping("/api/reportes/{reporteId}/comentarios")
    public List<ComentarioResponse> listar(@PathVariable String reporteId) {
        List<Comentario> comentarios = comentarioService.listarPorReporte(reporteId);
        Map<String, String> nombres = usuarioService.nombresPorId(comentarios.stream().map(Comentario::getUsuarioId).collect(Collectors.toSet()));
        return comentarios.stream().map(comentario -> comentarioMapper.toResponse(comentario, nombres.get(comentario.getUsuarioId()))).toList();
    }
    @PostMapping("/api/reportes/{reporteId}/comentarios") @ResponseStatus(HttpStatus.CREATED)
    public ComentarioResponse crear(@PathVariable String reporteId, @AuthenticationPrincipal UsuarioPrincipal principal, @Valid @RequestBody ComentarioCrearRequest request) { return aRespuesta(comentarioService.crear(reporteId, principal.getId(), request)); }
    @PutMapping("/api/comentarios/{id}")
    public ComentarioResponse actualizar(@PathVariable String id, @AuthenticationPrincipal UsuarioPrincipal principal, @Valid @RequestBody ComentarioActualizarRequest request) { return aRespuesta(comentarioService.actualizar(id, principal.getId(), request)); }
    @DeleteMapping("/api/comentarios/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable String id, @AuthenticationPrincipal UsuarioPrincipal principal) { comentarioService.eliminar(id, principal.getId()); }
    private ComentarioResponse aRespuesta(Comentario comentario) { return comentarioMapper.toResponse(comentario, usuarioService.nombrePorId(comentario.getUsuarioId())); }
}
