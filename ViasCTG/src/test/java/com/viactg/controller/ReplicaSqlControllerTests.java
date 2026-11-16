package com.viactg.controller;

import com.viactg.dto.EstadoReplicaResponse;
import com.viactg.exception.ServicioNoDisponibleException;
import com.viactg.model.Rol;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReplicaSqlControllerTests extends ControllerTestBase {

    @Test
    void elAdministradorVeElEstadoDeLaReplica() throws Exception {
        when(replicaSqlService.estado()).thenReturn(new EstadoReplicaResponse(true, true, false, Instant.parse("2026-11-16T20:00:00Z"),
                Instant.parse("2026-11-16T19:00:00Z"), null, null, List.of(new EstadoReplicaResponse.Tabla("reportes", 20_000, 20_000L))));

        mvc.perform(get("/api/admin/replica-sql").header(HttpHeaders.AUTHORIZATION, bearer(Rol.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.habilitada").value(true))
                .andExpect(jsonPath("$.conectada").value(true))
                .andExpect(jsonPath("$.tablas[0].nombre").value("reportes"))
                .andExpect(jsonPath("$.tablas[0].mongo").value(20_000))
                .andExpect(jsonPath("$.tablas[0].sql").value(20_000));
    }

    @Test
    void reconstruirResponde202YQuedaEnLaAuditoria() throws Exception {
        mvc.perform(post("/api/admin/replica-sql/reconstruir").header(HttpHeaders.AUTHORIZATION, bearer(Rol.ADMIN)))
                .andExpect(status().isAccepted());

        verify(replicaSqlService).solicitarReconstruccion();
        verify(auditoriaService).registrar(eq("admin"), eq("REPLICA_SQL_RECONSTRUIDA"), eq("SISTEMA"), eq("replica-sql"), anyString());
    }

    @Test
    void sinReplicaHabilitadaReconstruirResponde503SinAuditar() throws Exception {
        doThrow(new ServicioNoDisponibleException("La réplica SQL no está habilitada (SQL_REPLICA_HABILITADA=true)"))
                .when(replicaSqlService).solicitarReconstruccion();

        mvc.perform(post("/api/admin/replica-sql/reconstruir").header(HttpHeaders.AUTHORIZATION, bearer(Rol.ADMIN)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("La réplica SQL no está habilitada (SQL_REPLICA_HABILITADA=true)"));

        verify(auditoriaService, never()).registrar(anyString(), anyString(), anyString(), anyString(), anyString());
    }
}
