package com.viactg.controller;

import com.viactg.dto.EstadisticasResponse;
import com.viactg.service.EstadisticasService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/estadisticas")
@PreAuthorize("hasAnyRole('ADMIN', 'MODERADOR')")
public class EstadisticasController {
    private final EstadisticasService estadisticasService;
    public EstadisticasController(EstadisticasService estadisticasService) { this.estadisticasService = estadisticasService; }
    @GetMapping public EstadisticasResponse obtener() { return estadisticasService.calcular(); }
}
