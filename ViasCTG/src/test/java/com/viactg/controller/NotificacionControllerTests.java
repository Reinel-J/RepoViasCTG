package com.viactg.controller;

import com.viactg.exception.ResourceNotFoundException;
import com.viactg.model.Notificacion;
import com.viactg.model.Rol;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificacionControllerTests extends ControllerTestBase {
    private static Notificacion notificacion(boolean leida) {
        return Notificacion.builder().id("n1").usuarioId("ciudadano").reporteId("r1")
                .mensaje("Tu reporte pasó a revisión").leida(leida).fecha(Instant.parse("2026-10-22T08:00:00Z")).build();
    }

    @Test
    void listarDevuelveLasNotificacionesDelUsuarioDelToken() throws Exception {
        when(notificacionService.listarParaUsuario("ciudadano")).thenReturn(List.of(notificacion(false)));

        mvc.perform(get("/api/notificaciones").header(HttpHeaders.AUTHORIZATION, bearer(Rol.CIUDADANO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mensaje").value("Tu reporte pasó a revisión"))
                .andExpect(jsonPath("$[0].leida").value(false));
    }

    @Test
    void sinLeerDevuelveElTotalParaLaCampana() throws Exception {
        when(notificacionService.contarSinLeer("ciudadano")).thenReturn(3L);

        mvc.perform(get("/api/notificaciones/sin-leer").header(HttpHeaders.AUTHORIZATION, bearer(Rol.CIUDADANO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    void marcarLeidaUsaAlUsuarioDelToken() throws Exception {
        when(notificacionService.marcarLeida("n1", "ciudadano")).thenReturn(notificacion(true));

        mvc.perform(patch("/api/notificaciones/n1/leida").header(HttpHeaders.AUTHORIZATION, bearer(Rol.CIUDADANO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leida").value(true));
    }

    @Test
    void marcarLeidaUnaNotificacionAjenaDevuelve404() throws Exception {
        when(notificacionService.marcarLeida("n1", "moderador")).thenThrow(new ResourceNotFoundException("Notificación no encontrada"));

        mvc.perform(patch("/api/notificaciones/n1/leida").header(HttpHeaders.AUTHORIZATION, bearer(Rol.MODERADOR)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Notificación no encontrada"));
    }

    @Test
    void marcarTodasDevuelveCuantasCambiaron() throws Exception {
        when(notificacionService.marcarTodasLeidas("ciudadano")).thenReturn(2L);

        mvc.perform(patch("/api/notificaciones/leidas").header(HttpHeaders.AUTHORIZATION, bearer(Rol.CIUDADANO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marcadas").value(2));
    }
}
