package com.viactg.controller;
import com.viactg.dto.PaginaResponse;
import com.viactg.dto.RegistroAuditoriaResponse;
import com.viactg.service.AuditoriaService;
import jakarta.validation.constraints.Max; import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
@RestController @RequestMapping("/api/admin/auditoria") @PreAuthorize("hasRole('ADMIN')") @Validated
public class AuditoriaController {
 private final AuditoriaService auditoriaService; public AuditoriaController(AuditoriaService auditoriaService) { this.auditoriaService = auditoriaService; }
 @GetMapping public PaginaResponse<RegistroAuditoriaResponse> listar(@RequestParam(required=false) String entidad, @RequestParam(required=false) String accion, @RequestParam(required=false) String actorId, @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant desde, @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant hasta, @RequestParam(defaultValue="0") @Min(value=0,message="La página no puede ser negativa") int pagina, @RequestParam(defaultValue="50") @Min(value=1,message="El tamaño de página debe ser al menos 1") @Max(value=100,message="El tamaño de página no puede superar 100") int tamano) { return auditoriaService.listar(entidad, accion, actorId, desde, hasta, pagina, tamano); }
}
