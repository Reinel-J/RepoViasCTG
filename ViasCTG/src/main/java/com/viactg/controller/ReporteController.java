package com.viactg.controller;

import com.viactg.dto.PosibleDuplicadoResponse;
import com.viactg.dto.ReporteActualizarRequest;
import com.viactg.dto.ReporteCrearRequest;
import com.viactg.dto.ReporteResponse;
import com.viactg.dto.ResumenReportesResponse;
import com.viactg.mapper.ReporteMapper;
import com.viactg.model.EstadoReporte;
import com.viactg.security.UsuarioPrincipal;
import com.viactg.service.DuplicadoService;
import com.viactg.service.ReporteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

@RestController
@Validated
@RequestMapping("/api/reportes")
public class ReporteController {
    private final ReporteService reporteService;
    private final DuplicadoService duplicadoService;
    private final ReporteMapper reporteMapper;
    public ReporteController(ReporteService reporteService, DuplicadoService duplicadoService, ReporteMapper reporteMapper) { this.reporteService = reporteService; this.duplicadoService = duplicadoService; this.reporteMapper = reporteMapper; }

    // Del más reciente al más antiguo. "limite" devuelve solo los N más recientes (por ejemplo, para la portada).
    @GetMapping
    public List<ReporteResponse> listar(@RequestParam(required = false) EstadoReporte estado,
                                        @RequestParam(required = false) @Min(value = 1, message = "El límite debe ser al menos 1")
                                        @Max(value = 100, message = "El límite no puede superar 100") Integer limite) {
        return reporteService.listar(estado, limite).stream().map(reporteMapper::toResponse).toList();
    }

    @GetMapping("/resumen")
    public ResumenReportesResponse resumen() {
        ReporteService.Resumen resumen = reporteService.calcularResumen(Instant.now());
        return new ResumenReportesResponse(resumen.total(), resumen.porEstado(), resumen.creadosUltimas24Horas(), resumen.barrios());
    }

    @GetMapping("/cercanos")
    public List<ReporteResponse> listarCercanos(
            @RequestParam @DecimalMin(value = "-90.0", message = "La latitud debe estar entre -90 y 90")
            @DecimalMax(value = "90.0", message = "La latitud debe estar entre -90 y 90") double lat,
            @RequestParam @DecimalMin(value = "-180.0", message = "La longitud debe estar entre -180 y 180")
            @DecimalMax(value = "180.0", message = "La longitud debe estar entre -180 y 180") double lng,
            @RequestParam(defaultValue = "2") @Positive(message = "El radio debe ser mayor que cero")
            @DecimalMax(value = "50", message = "El radio no puede superar 50 km") double radioKm,
            @RequestParam(required = false) EstadoReporte estado) {
        return reporteService.listarCercanos(lat, lng, radioKm, estado).stream().map(reporteMapper::toResponse).toList();
    }
    @GetMapping("/posibles-duplicados")
    public List<PosibleDuplicadoResponse> posiblesDuplicados(
            @RequestParam @DecimalMin(value = "-90.0", message = "La latitud debe estar entre -90 y 90")
            @DecimalMax(value = "90.0", message = "La latitud debe estar entre -90 y 90") double lat,
            @RequestParam @DecimalMin(value = "-180.0", message = "La longitud debe estar entre -180 y 180")
            @DecimalMax(value = "180.0", message = "La longitud debe estar entre -180 y 180") double lng) {
        return duplicadoService.buscar(lat, lng).stream().map(duplicado -> new PosibleDuplicadoResponse(
                reporteMapper.toResponse(duplicado.reporte()), duplicado.distanciaMetros(), duplicado.confirmaciones())).toList();
    }
    @GetMapping("/{id}") public ReporteResponse obtener(@PathVariable String id) { return reporteMapper.toResponse(reporteService.buscarPorId(id)); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ReporteResponse crear(@AuthenticationPrincipal UsuarioPrincipal principal, @Valid @RequestBody ReporteCrearRequest request) { return reporteMapper.toResponse(reporteService.crear(request, principal.getId())); }
    @PutMapping("/{id}")
    public ReporteResponse actualizar(@PathVariable String id, @AuthenticationPrincipal UsuarioPrincipal principal, @Valid @RequestBody ReporteActualizarRequest request) { return reporteMapper.toResponse(reporteService.actualizar(id, request, principal.getId())); }
    @PostMapping(path = "/{id}/fotos", consumes = "multipart/form-data")
    public ReporteResponse subirFotos(@PathVariable String id, @AuthenticationPrincipal UsuarioPrincipal principal,
                                      @RequestParam("archivos") List<MultipartFile> archivos) {
        return reporteMapper.toResponse(reporteService.subirFotos(id, archivos, principal.getId()));
    }
}
