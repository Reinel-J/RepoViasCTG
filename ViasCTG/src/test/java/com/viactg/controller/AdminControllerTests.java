package com.viactg.controller;

import com.viactg.exception.BusinessRuleException;
import com.viactg.model.EstadoReporte;
import com.viactg.model.HistorialEstado;
import com.viactg.model.PrioridadReporte;
import com.viactg.model.Rol;
import com.viactg.service.SeguimientoService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminControllerTests extends ControllerTestBase {

    @Test
    void pendientesListaSoloLosReportesPendientes() throws Exception {
        when(reporteService.listar(EstadoReporte.PENDIENTE)).thenReturn(List.of(reporte("r1", "ciudadano")));

        mvc.perform(get("/api/admin/reportes/pendientes").header(HttpHeaders.AUTHORIZATION, bearer(Rol.MODERADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("r1"));
    }

    @Test
    void cambiarEstadoPasaElModeradorElComentarioYLaPrioridad() throws Exception {
        when(reporteService.cambiarEstadoReporte(eq("r1"), any(), anyString(), any(), any())).thenReturn(reporte("r1", "ciudadano"));

        mvc.perform(patch("/api/admin/reportes/r1/estado").header(HttpHeaders.AUTHORIZATION, bearer(Rol.MODERADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoEstado\":\"EN_REVISION\",\"comentario\":\"Cuadrilla asignada\",\"prioridad\":\"ALTA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("r1"));

        verify(reporteService).cambiarEstadoReporte("r1", EstadoReporte.EN_REVISION, "moderador", "Cuadrilla asignada", PrioridadReporte.ALTA);
    }

    @Test
    void sinComentarioNiPrioridadSePasanComoNull() throws Exception {
        when(reporteService.cambiarEstadoReporte(eq("r1"), any(), anyString(), any(), any())).thenReturn(reporte("r1", "ciudadano"));

        mvc.perform(patch("/api/admin/reportes/r1/estado").header(HttpHeaders.AUTHORIZATION, bearer(Rol.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nuevoEstado\":\"RECHAZADO\"}"))
                .andExpect(status().isOk());

        verify(reporteService).cambiarEstadoReporte(eq("r1"), eq(EstadoReporte.RECHAZADO), eq("admin"), isNull(), isNull());
    }

    @Test
    void cambiarEstadoSinNuevoEstadoDevuelve400() throws Exception {
        mvc.perform(patch("/api/admin/reportes/r1/estado").header(HttpHeaders.AUTHORIZATION, bearer(Rol.MODERADOR))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comentario\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.nuevoEstado").value("El nuevo estado es obligatorio"))
                .andExpect(jsonPath("$.validationErrors.comentario").value("El comentario admite máximo 500 caracteres"));

        verifyNoInteractions(reporteService);
    }

    @Test
    void unEstadoOPrioridadQueNoExisteDevuelve400() throws Exception {
        mvc.perform(patch("/api/admin/reportes/r1/estado").header(HttpHeaders.AUTHORIZATION, bearer(Rol.MODERADOR))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nuevoEstado\":\"ARREGLADO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(startsWith("El cuerpo de la solicitud falta o no es un JSON válido")));
        mvc.perform(patch("/api/admin/reportes/r1/estado").header(HttpHeaders.AUTHORIZATION, bearer(Rol.MODERADOR))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nuevoEstado\":\"EN_REVISION\",\"prioridad\":\"URGENTE\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(reporteService);
    }

    @Test
    void unaTransicionNoPermitidaDevuelve409() throws Exception {
        when(reporteService.cambiarEstadoReporte(eq("r1"), any(), anyString(), any(), any()))
                .thenThrow(new BusinessRuleException("No se puede pasar de RESUELTO a PENDIENTE"));

        mvc.perform(patch("/api/admin/reportes/r1/estado").header(HttpHeaders.AUTHORIZATION, bearer(Rol.MODERADOR))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nuevoEstado\":\"PENDIENTE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No se puede pasar de RESUELTO a PENDIENTE"));
    }

    @Test
    void historialDevuelveLosCambiosDeEstado() throws Exception {
        when(reporteService.historial("r1")).thenReturn(List.of(new HistorialEstado("h1", "moderador", EstadoReporte.PENDIENTE,
                EstadoReporte.EN_REVISION, "Revisando", Instant.parse("2026-10-21T10:00:00Z"))));

        mvc.perform(get("/api/admin/reportes/r1/historial").header(HttpHeaders.AUTHORIZATION, bearer(Rol.MODERADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estadoAnterior").value("PENDIENTE"))
                .andExpect(jsonPath("$[0].estadoNuevo").value("EN_REVISION"))
                .andExpect(jsonPath("$[0].comentarioAdmin").value("Revisando"));
    }

    @Test
    void seguimientoListaLosVotosDeCadaReporte() throws Exception {
        when(seguimientoService.resumenes()).thenReturn(List.of(new SeguimientoService.Resumen("r1", 3, 1, null, null, Instant.parse("2026-10-25T08:00:00Z"))));

        mvc.perform(get("/api/admin/reportes/seguimiento").header(HttpHeaders.AUTHORIZATION, bearer(Rol.MODERADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reporteId").value("r1"))
                .andExpect(jsonPath("$[0].arreglado").value(3))
                .andExpect(jsonPath("$[0].sigue").value(1))
                .andExpect(jsonPath("$[0].cierrePrevisto").value("2026-10-25T08:00:00Z"));
    }

    @Test
    void ejecutarLaTareaDiariaDevuelveRecordatoriosYCierresPorLaComunidad() throws Exception {
        when(seguimientoService.ejecutarTareaDiaria(any())).thenReturn(new SeguimientoService.Ejecucion(4, 2));

        mvc.perform(post("/api/admin/reportes/seguimiento/recordatorios").header(HttpHeaders.AUTHORIZATION, bearer(Rol.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportesRecordados").value(4))
                .andExpect(jsonPath("$.reportesCerrados").value(2));
    }

    @Test
    void mantenerAbiertoDescartaLaPropuestaDeCierre() throws Exception {
        when(seguimientoService.mantenerAbierto("r1")).thenReturn(new SeguimientoService.Resumen("r1", 3, 0, null, null, null));

        mvc.perform(post("/api/admin/reportes/r1/mantener-abierto").header(HttpHeaders.AUTHORIZATION, bearer(Rol.MODERADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reporteId").value("r1"))
                .andExpect(jsonPath("$.cierrePrevisto").isEmpty());
    }

    @Test
    void mantenerAbiertoUnReporteQueNoEstaEnProcesoDevuelve409() throws Exception {
        when(seguimientoService.mantenerAbierto("r1")).thenThrow(new BusinessRuleException(
                "Solo un reporte en proceso puede tener una propuesta de cierre por la comunidad"));

        mvc.perform(post("/api/admin/reportes/r1/mantener-abierto").header(HttpHeaders.AUTHORIZATION, bearer(Rol.MODERADOR)))
                .andExpect(status().isConflict());
    }
}
