package com.viactg.controller;

import com.viactg.dto.DashboardResponse;
import com.viactg.exception.ServicioNoDisponibleException;
import com.viactg.model.Rol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpHeaders;

import java.time.Instant;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardControllerTests extends ControllerTestBase {
    private static final String URL = "http://localhost:3000/embed/dashboard/a.b.c#bordered=false&titled=true";

    @Test
    void sinSesionResponde401() throws Exception {
        mvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(metabaseService);
    }

    @Test
    void unCiudadanoNoVeElDashboard() throws Exception {
        mvc.perform(get("/api/admin/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(Rol.CIUDADANO)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("No tienes permiso para realizar esta acción"));

        verifyNoInteractions(metabaseService);
    }

    @ParameterizedTest
    @EnumSource(value = Rol.class, names = {"MODERADOR", "ADMIN"})
    void moderadoresYAdministradoresRecibenLaUrlFirmada(Rol rol) throws Exception {
        when(metabaseService.urlDashboard()).thenReturn(new DashboardResponse(URL, Instant.parse("2026-10-30T15:10:00Z")));

        mvc.perform(get("/api/admin/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(rol)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(URL))
                .andExpect(jsonPath("$.expiraEn").value("2026-10-30T15:10:00Z"));
    }

    @Test
    void sinConfiguracionResponde503ConErrorResponse() throws Exception {
        when(metabaseService.urlDashboard()).thenThrow(new ServicioNoDisponibleException("El dashboard no está configurado"));

        mvc.perform(get("/api/admin/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(Rol.ADMIN)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("El dashboard no está configurado"))
                .andExpect(jsonPath("$.validationErrors").isEmpty());
    }
}
