package com.viactg.controller;

import com.viactg.dto.CambioEstadoRequest;
import com.viactg.dto.HistorialEstadoResponse;
import com.viactg.dto.RecordatoriosResponse;
import com.viactg.dto.ReporteResponse;
import com.viactg.dto.SeguimientoResumenResponse;
import com.viactg.mapper.ReporteMapper;
import com.viactg.model.EstadoReporte;
import com.viactg.security.UsuarioPrincipal;
import com.viactg.service.ReporteService;
import com.viactg.service.SeguimientoService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/admin/reportes")
@PreAuthorize("hasAnyRole('ADMIN', 'MODERADOR')")
public class AdminController {
    private final ReporteService reporteService;
    private final SeguimientoService seguimientoService;
    private final ReporteMapper reporteMapper;
    public AdminController(ReporteService reporteService, SeguimientoService seguimientoService, ReporteMapper reporteMapper) { this.reporteService = reporteService; this.seguimientoService = seguimientoService; this.reporteMapper = reporteMapper; }
    @GetMapping("/pendientes") public List<ReporteResponse> pendientes() { return reporteService.listar(EstadoReporte.PENDIENTE).stream().map(reporteMapper::toResponse).toList(); }
    @PatchMapping("/{id}/estado")
    public ReporteResponse cambiarEstado(@PathVariable String id, @AuthenticationPrincipal UsuarioPrincipal principal, @Valid @RequestBody CambioEstadoRequest request) {
        return reporteMapper.toResponse(reporteService.cambiarEstadoReporte(id, request.nuevoEstado(), principal.getId(), request.comentario(), request.prioridad()));
    }
    @GetMapping("/{id}/historial") public List<HistorialEstadoResponse> historial(@PathVariable String id) { return reporteService.historial(id).stream().map(reporteMapper::toHistorialResponse).toList(); }
    @GetMapping("/seguimiento") public List<SeguimientoResumenResponse> seguimientos() { return seguimientoService.resumenes().stream().map(SeguimientoController::aRespuesta).toList(); }
    // Ejecuta ya la tarea diaria; respeta el mismo intervalo, así que repetirla no vuelve a avisar.
    @PostMapping("/seguimiento/recordatorios") @PreAuthorize("hasRole('ADMIN')")
    public RecordatoriosResponse enviarRecordatorios() { return new RecordatoriosResponse(seguimientoService.enviarRecordatorios(Instant.now())); }
}
