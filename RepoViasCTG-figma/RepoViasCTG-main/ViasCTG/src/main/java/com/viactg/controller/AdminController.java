package com.viactg.controller;

import com.viactg.dto.CambioEstadoRequest;
import com.viactg.dto.HistorialEstadoResponse;
import com.viactg.dto.ReporteResponse;
import com.viactg.mapper.ReporteMapper;
import com.viactg.model.EstadoReporte;
import com.viactg.security.UsuarioPrincipal;
import com.viactg.service.ReporteService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/reportes")
@PreAuthorize("hasAnyRole('ADMIN', 'MODERADOR')")
public class AdminController {
    private final ReporteService reporteService;
    private final ReporteMapper reporteMapper;
    public AdminController(ReporteService reporteService, ReporteMapper reporteMapper) { this.reporteService = reporteService; this.reporteMapper = reporteMapper; }
    @GetMapping("/pendientes") public List<ReporteResponse> pendientes() { return reporteService.listar(EstadoReporte.PENDIENTE).stream().map(reporteMapper::toResponse).toList(); }
    @PatchMapping("/{id}/estado")
    public ReporteResponse cambiarEstado(@PathVariable String id, @AuthenticationPrincipal UsuarioPrincipal principal, @Valid @RequestBody CambioEstadoRequest request) {
        return reporteMapper.toResponse(reporteService.cambiarEstadoReporte(id, request.nuevoEstado(), principal.getId(), request.comentario()));
    }
    @GetMapping("/{id}/historial") public List<HistorialEstadoResponse> historial(@PathVariable String id) { return reporteService.historial(id).stream().map(reporteMapper::toHistorialResponse).toList(); }
}
