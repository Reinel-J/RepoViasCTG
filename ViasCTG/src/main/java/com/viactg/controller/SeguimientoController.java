package com.viactg.controller;

import com.viactg.dto.SeguimientoRequest;
import com.viactg.dto.SeguimientoResumenResponse;
import com.viactg.security.UsuarioPrincipal;
import com.viactg.service.SeguimientoService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reportes/{reporteId}/seguimiento")
public class SeguimientoController {
    private final SeguimientoService seguimientoService;
    public SeguimientoController(SeguimientoService seguimientoService) { this.seguimientoService = seguimientoService; }

    // Público: sin sesión, principal es null y miRespuesta sale vacía.
    @GetMapping
    public SeguimientoResumenResponse obtener(@PathVariable String reporteId, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return aRespuesta(seguimientoService.resumen(reporteId, principal == null ? null : principal.getId()));
    }

    @PostMapping
    public SeguimientoResumenResponse responder(@PathVariable String reporteId, @AuthenticationPrincipal UsuarioPrincipal principal,
                                                @Valid @RequestBody SeguimientoRequest request) {
        return aRespuesta(seguimientoService.responder(reporteId, principal.getId(), request.arreglado()));
    }

    static SeguimientoResumenResponse aRespuesta(SeguimientoService.Resumen resumen) {
        return new SeguimientoResumenResponse(resumen.reporteId(), resumen.arreglado(), resumen.sigue(),
                resumen.ultimaRespuesta(), resumen.miRespuesta(), resumen.cierrePrevisto());
    }
}
