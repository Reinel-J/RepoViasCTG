package com.viactg.controller;

import com.viactg.dto.ConfirmacionResponse;
import com.viactg.mapper.ConfirmacionMapper;
import com.viactg.security.UsuarioPrincipal;
import com.viactg.service.ConfirmacionService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/reportes/{reporteId}/confirmaciones")
public class ConfirmacionController {
    private final ConfirmacionService confirmacionService;
    private final ConfirmacionMapper confirmacionMapper;
    public ConfirmacionController(ConfirmacionService confirmacionService, ConfirmacionMapper confirmacionMapper) { this.confirmacionService = confirmacionService; this.confirmacionMapper = confirmacionMapper; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ConfirmacionResponse crear(@PathVariable String reporteId, @AuthenticationPrincipal UsuarioPrincipal principal) { return confirmacionMapper.toResponse(confirmacionService.crear(reporteId, principal.getId())); }
    @GetMapping
    public List<ConfirmacionResponse> listar(@PathVariable String reporteId) { return confirmacionService.listarPorReporte(reporteId).stream().map(confirmacionMapper::toResponse).toList(); }
    @DeleteMapping("/me") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retirar(@PathVariable String reporteId, @AuthenticationPrincipal UsuarioPrincipal principal) { confirmacionService.retirar(reporteId, principal.getId()); }
}
