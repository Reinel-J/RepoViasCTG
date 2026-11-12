package com.viactg.controller;

import com.viactg.dto.CambioEstadoRequest;
import com.viactg.dto.CambioEstadoLoteRequest;
import com.viactg.dto.CambioEstadoLoteResponse;
import com.viactg.dto.HistorialEstadoResponse;
import com.viactg.dto.RecordatoriosResponse;
import com.viactg.dto.ReporteResponse;
import com.viactg.dto.SeguimientoResumenResponse;
import com.viactg.mapper.ReporteMapper;
import com.viactg.model.EstadoReporte;
import com.viactg.security.UsuarioPrincipal;
import com.viactg.service.ReporteService;
import com.viactg.service.SeguimientoService;
import com.viactg.service.AuditoriaService;
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
    private final AuditoriaService auditoriaService;
    public AdminController(ReporteService reporteService, SeguimientoService seguimientoService, ReporteMapper reporteMapper, AuditoriaService auditoriaService) { this.reporteService = reporteService; this.seguimientoService = seguimientoService; this.reporteMapper = reporteMapper; this.auditoriaService = auditoriaService; }
    @GetMapping("/pendientes") public List<ReporteResponse> pendientes() { return reporteService.listar(EstadoReporte.PENDIENTE).stream().map(reporteMapper::toResponse).toList(); }
    @PatchMapping("/{id}/estado")
    public ReporteResponse cambiarEstado(@PathVariable String id, @AuthenticationPrincipal UsuarioPrincipal principal, @Valid @RequestBody CambioEstadoRequest request) {
        return reporteMapper.toResponse(reporteService.cambiarEstadoReporte(id, request.nuevoEstado(), principal.getId(), request.comentario(), request.prioridad()));
    }
    @PostMapping("/estado-lote") @PreAuthorize("hasAnyRole('ADMIN', 'MODERADOR')")
    public CambioEstadoLoteResponse cambiarEstadoLote(@AuthenticationPrincipal UsuarioPrincipal principal,
                                                      @Valid @RequestBody CambioEstadoLoteRequest request) {
        return reporteService.cambiarEstadoLote(request, principal.getId());
    }
    @GetMapping("/{id}/historial") public List<HistorialEstadoResponse> historial(@PathVariable String id) { return reporteService.historial(id).stream().map(reporteMapper::toHistorialResponse).toList(); }
    @GetMapping("/seguimiento") public List<SeguimientoResumenResponse> seguimientos() { return seguimientoService.resumenes().stream().map(SeguimientoController::aRespuesta).toList(); }
    // Ejecuta ya la tarea diaria (recordatorios y cierres por la comunidad); respeta los mismos plazos, así que repetirla
    // no vuelve a avisar ni adelanta un cierre.
    @PostMapping("/seguimiento/recordatorios") @PreAuthorize("hasRole('ADMIN')")
    public RecordatoriosResponse enviarRecordatorios(@AuthenticationPrincipal UsuarioPrincipal principal) {
        SeguimientoService.Ejecucion ejecucion = seguimientoService.ejecutarTareaDiaria(Instant.now());
        auditoriaService.registrar(principal.getId(), "SEGUIMIENTO_TAREA_EJECUTADA", "SISTEMA", "seguimiento",
                ejecucion.recordados() + " recordatorios, " + ejecucion.cerrados() + " cierres comunitarios");
        return new RecordatoriosResponse(ejecucion.recordados(), ejecucion.cerrados());
    }
    // Descarta la propuesta de cierre por los votos; solo contarán los que lleguen desde ahora.
    @PostMapping("/{id}/mantener-abierto")
    public SeguimientoResumenResponse mantenerAbierto(@PathVariable String id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        SeguimientoResumenResponse respuesta = SeguimientoController.aRespuesta(seguimientoService.mantenerAbierto(id));
        auditoriaService.registrar(principal.getId(), "REPORTE_MANTENIDO_ABIERTO", "REPORTE", id, "Se descartó la propuesta de cierre comunitario");
        return respuesta;
    }
}
