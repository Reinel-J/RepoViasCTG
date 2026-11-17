package com.viactg.controller;

import com.viactg.dto.EstadoReplicaResponse;
import com.viactg.security.UsuarioPrincipal;
import com.viactg.service.AuditoriaService;
import com.viactg.service.ReplicaSqlService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Estado de la base secundaria (réplica en PostgreSQL) y reconstrucción manual. Solo administradores. */
@RestController
@RequestMapping("/api/admin/replica-sql")
@PreAuthorize("hasRole('ADMIN')")
public class ReplicaSqlController {
    private final ReplicaSqlService replicaSqlService;
    private final AuditoriaService auditoriaService;

    public ReplicaSqlController(ReplicaSqlService replicaSqlService, AuditoriaService auditoriaService) {
        this.replicaSqlService = replicaSqlService;
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public EstadoReplicaResponse estado() {
        return replicaSqlService.estado();
    }

    // La reconstrucción corre en segundo plano: 202 y el panel consulta el estado para ver cuándo terminó.
    @PostMapping("/reconstruir")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void reconstruir(@AuthenticationPrincipal UsuarioPrincipal principal) {
        replicaSqlService.solicitarReconstruccion();
        auditoriaService.registrar(principal.getId(), "REPLICA_SQL_RECONSTRUIDA", "SISTEMA", "replica-sql",
                "Reconstrucción completa de la réplica PostgreSQL");
    }
}
