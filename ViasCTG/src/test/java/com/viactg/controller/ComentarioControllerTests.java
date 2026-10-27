package com.viactg.controller;

import com.viactg.dto.ComentarioActualizarRequest;
import com.viactg.dto.ComentarioCrearRequest;
import com.viactg.exception.ForbiddenOperationException;
import com.viactg.model.Comentario;
import com.viactg.model.Rol;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ComentarioControllerTests extends ControllerTestBase {
    private static Comentario comentario(String id, String autorId, String texto) {
        return Comentario.builder().id(id).reporteId("r1").usuarioId(autorId).texto(texto)
                .fecha(Instant.parse("2026-10-21T10:00:00Z")).build();
    }

    @Test
    void listarAgregaElNombreDeCadaAutorConUnaSolaConsulta() throws Exception {
        when(comentarioService.listarPorReporte("r1")).thenReturn(List.of(comentario("k1", "u1", "Sigue igual"),
                comentario("k2", "u2", "Ya pusieron conos"), comentario("k3", "u1", "Empeoró con la lluvia")));
        when(usuarioService.nombresPorId(Set.of("u1", "u2"))).thenReturn(Map.of("u1", "Ana", "u2", "Luis"));

        mvc.perform(get("/api/reportes/r1/comentarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].usuarioNombre").value("Ana"))
                .andExpect(jsonPath("$[1].usuarioNombre").value("Luis"))
                .andExpect(jsonPath("$[2].usuarioNombre").value("Ana"))
                .andExpect(jsonPath("$[1].texto").value("Ya pusieron conos"));
    }

    @Test
    void crearUsaAlUsuarioDelTokenYDevuelve201ConSuNombre() throws Exception {
        when(comentarioService.crear("r1", "ciudadano", new ComentarioCrearRequest("Sigue igual")))
                .thenReturn(comentario("k1", "ciudadano", "Sigue igual"));
        when(usuarioService.nombrePorId("ciudadano")).thenReturn("Ana");

        mvc.perform(post("/api/reportes/r1/comentarios").header(HttpHeaders.AUTHORIZATION, bearer(Rol.CIUDADANO))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"Sigue igual\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("k1"))
                .andExpect(jsonPath("$.usuarioNombre").value("Ana"))
                .andExpect(jsonPath("$.editado").value(false));
    }

    @Test
    void unComentarioVacioOMuyLargoDevuelve400() throws Exception {
        mvc.perform(post("/api/reportes/r1/comentarios").header(HttpHeaders.AUTHORIZATION, bearer(Rol.CIUDADANO))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.texto").value("El comentario no puede estar vacío"));
        mvc.perform(post("/api/reportes/r1/comentarios").header(HttpHeaders.AUTHORIZATION, bearer(Rol.CIUDADANO))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"" + "a".repeat(1001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.texto").value("El comentario admite máximo 1000 caracteres"));

        verifyNoInteractions(comentarioService);
    }

    @Test
    void editarElComentarioDeOtraPersonaDevuelve403() throws Exception {
        when(comentarioService.actualizar("k1", "ciudadano", new ComentarioActualizarRequest("Texto cambiado")))
                .thenThrow(new ForbiddenOperationException("Solo el autor puede editar este comentario"));

        mvc.perform(put("/api/comentarios/k1").header(HttpHeaders.AUTHORIZATION, bearer(Rol.CIUDADANO))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"Texto cambiado\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Solo el autor puede editar este comentario"));
    }

    @Test
    void eliminarResponde204() throws Exception {
        mvc.perform(delete("/api/comentarios/k1").header(HttpHeaders.AUTHORIZATION, bearer(Rol.MODERADOR)))
                .andExpect(status().isNoContent());

        verify(comentarioService).eliminar("k1", "moderador");
    }

    @Test
    void eliminarSinPermisoDevuelve403() throws Exception {
        doThrow(new ForbiddenOperationException("No puedes eliminar este comentario")).when(comentarioService).eliminar(any(), any());

        mvc.perform(delete("/api/comentarios/k1").header(HttpHeaders.AUTHORIZATION, bearer(Rol.CIUDADANO)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("No puedes eliminar este comentario"));
    }
}
