package com.viactg.controller;

import com.viactg.dto.ReporteActualizarRequest;
import com.viactg.dto.ReporteCrearRequest;
import com.viactg.dto.ReporteResponse;
import com.viactg.mapper.ReporteMapper;
import com.viactg.model.EstadoReporte;
import com.viactg.security.UsuarioPrincipal;
import com.viactg.service.ReporteService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@Validated
@RequestMapping("/api/reportes")
public class ReporteController {
    private final ReporteService reporteService;
    private final ReporteMapper reporteMapper;
    public ReporteController(ReporteService reporteService, ReporteMapper reporteMapper) { this.reporteService = reporteService; this.reporteMapper = reporteMapper; }
    @GetMapping public List<ReporteResponse> listar(@RequestParam(required = false) EstadoReporte estado) { return reporteService.listar(estado).stream().map(reporteMapper::toResponse).toList(); }
    @GetMapping("/cercanos")
    public List<ReporteResponse> listarCercanos(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double lng,
            @RequestParam(defaultValue = "2") @Positive double radioKm,
            @RequestParam(required = false) EstadoReporte estado) {
        return reporteService.listarCercanos(lat, lng, radioKm, estado).stream().map(reporteMapper::toResponse).toList();
    }
    @GetMapping("/{id}") public ReporteResponse obtener(@PathVariable String id) { return reporteMapper.toResponse(reporteService.buscarPorId(id)); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ReporteResponse crear(@AuthenticationPrincipal UsuarioPrincipal principal, @Valid @RequestBody ReporteCrearRequest request) { return reporteMapper.toResponse(reporteService.crear(request, principal.getId())); }
    @PutMapping("/{id}")
    public ReporteResponse actualizar(@PathVariable String id, @AuthenticationPrincipal UsuarioPrincipal principal, @Valid @RequestBody ReporteActualizarRequest request) { return reporteMapper.toResponse(reporteService.actualizar(id, request, principal.getId())); }
}
