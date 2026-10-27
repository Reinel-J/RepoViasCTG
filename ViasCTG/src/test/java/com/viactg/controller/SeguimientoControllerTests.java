package com.viactg.controller;

import com.viactg.exception.BusinessRuleException;
import com.viactg.model.Rol;
import com.viactg.service.SeguimientoService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeguimientoControllerTests extends ControllerTestBase {
    private static final Instant ULTIMA = Instant.parse("2026-10-22T08:00:00Z");

    @Test
    void sinSesionSeConsultaSinUsuarioYMiRespuestaSaleVacia() throws Exception {
        when(seguimientoService.resumen("r1", null)).thenReturn(new SeguimientoService.Resumen("r1", 2, 1, ULTIMA, null, null));

        mvc.perform(get("/api/reportes/r1/seguimiento"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arreglado").value(2))
                .andExpect(jsonPath("$.sigue").value(1))
                .andExpect(jsonPath("$.ultimaRespuesta").value("2026-10-22T08:00:00Z"))
                .andExpect(jsonPath("$.miRespuesta").isEmpty())
                .andExpect(jsonPath("$.cierrePrevisto").isEmpty());
    }

    @Test
    void siLaComunidadProponeCerrarloIndicaCuando() throws Exception {
        when(seguimientoService.resumen("r1", null)).thenReturn(
                new SeguimientoService.Resumen("r1", 3, 0, ULTIMA, null, Instant.parse("2026-10-25T08:00:00Z")));

        mvc.perform(get("/api/reportes/r1/seguimiento"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cierrePrevisto").value("2026-10-25T08:00:00Z"));
    }

    @Test
    void conSesionIncluyeElVotoDelUsuario() throws Exception {
        when(seguimientoService.resumen("r1", "ciudadano")).thenReturn(new SeguimientoService.Resumen("r1", 2, 1, ULTIMA, true, null));

        mvc.perform(get("/api/reportes/r1/seguimiento").header(HttpHeaders.AUTHORIZATION, bearer(Rol.CIUDADANO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.miRespuesta").value(true));
    }

    @Test
    void responderRegistraElVotoDelUsuarioDelToken() throws Exception {
        when(seguimientoService.responder("r1", "ciudadano", false)).thenReturn(new SeguimientoService.Resumen("r1", 0, 1, ULTIMA, false, null));

        mvc.perform(post("/api/reportes/r1/seguimiento").header(HttpHeaders.AUTHORIZATION, bearer(Rol.CIUDADANO))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"arreglado\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sigue").value(1))
                .andExpect(jsonPath("$.miRespuesta").value(false));
    }

    @Test
    void responderSinIndicarSiFueArregladoDevuelve400() throws Exception {
        mvc.perform(post("/api/reportes/r1/seguimiento").header(HttpHeaders.AUTHORIZATION, bearer(Rol.CIUDADANO))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.arreglado").value("Indica si el problema ya fue arreglado"));

        verifyNoInteractions(seguimientoService);
    }

    @Test
    void responderSobreUnCasoCerradoDevuelve409() throws Exception {
        when(seguimientoService.responder(anyString(), anyString(), anyBoolean()))
                .thenThrow(new BusinessRuleException("Este reporte ya está cerrado"));

        mvc.perform(post("/api/reportes/r1/seguimiento").header(HttpHeaders.AUTHORIZATION, bearer(Rol.CIUDADANO))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"arreglado\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Este reporte ya está cerrado"));
    }
}
