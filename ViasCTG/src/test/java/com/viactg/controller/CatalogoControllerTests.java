package com.viactg.controller;

import com.viactg.dto.BarrioRequest;
import com.viactg.dto.CalleRequest;
import com.viactg.exception.BusinessRuleException;
import com.viactg.exception.DuplicateResourceException;
import com.viactg.exception.InvalidRequestException;
import com.viactg.model.Barrio;
import com.viactg.model.Calle;
import com.viactg.model.Categoria;
import com.viactg.model.Confirmacion;
import com.viactg.model.Rol;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Barrios, categorías y confirmaciones: controladores delgados con pocas reglas propias. */
class CatalogoControllerTests extends ControllerTestBase {

    @Test
    void crearBarrioDevuelve201ConSusCalles() throws Exception {
        BarrioRequest request = new BarrioRequest("Manga", "Histórica", List.of(new CalleRequest("Calle Real", "130001")));
        when(barrioService.crear(request)).thenReturn(Barrio.builder().id("b1").nombre("Manga").localidad("Histórica")
                .calles(List.of(new Calle("c1", "Calle Real", "130001"))).build());

        mvc.perform(post("/api/barrios").header(HttpHeaders.AUTHORIZATION, bearer(Rol.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Manga\",\"localidad\":\"Histórica\",\"calles\":[{\"nombre\":\"Calle Real\",\"codigoPostal\":\"130001\"}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("b1"))
                .andExpect(jsonPath("$.calles[0].id").value("c1"));
    }

    @Test
    void lasCallesDeUnBarrioTambienSeValidan() throws Exception {
        mvc.perform(post("/api/barrios").header(HttpHeaders.AUTHORIZATION, bearer(Rol.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Manga\",\"localidad\":\"Histórica\",\"calles\":[{\"nombre\":\"\",\"codigoPostal\":\"130001\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors['calles[0].nombre']").value("El nombre de la calle es obligatorio"));

        verifyNoInteractions(barrioService);
    }

    @Test
    void editarUnBarrioConCallesEnElCuerpoDevuelve400() throws Exception {
        when(barrioService.actualizar(eq("b1"), any())).thenThrow(new InvalidRequestException(
                "Las calles no se modifican al editar el barrio: agrégalas o quítalas con POST /api/barrios/{id}/calles"));

        mvc.perform(put("/api/barrios/b1").header(HttpHeaders.AUTHORIZATION, bearer(Rol.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Manga\",\"localidad\":\"Histórica\",\"calles\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(startsWith("Las calles no se modifican al editar el barrio")));
    }

    @Test
    void eliminarUnBarrioEnUsoDevuelve409() throws Exception {
        doThrow(new BusinessRuleException("No se puede eliminar un barrio con calles")).when(barrioService).eliminar("b1");

        mvc.perform(delete("/api/barrios/b1").header(HttpHeaders.AUTHORIZATION, bearer(Rol.ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No se puede eliminar un barrio con calles"));
    }

    @Test
    void lasCategoriasListanSoloLasActivasPorDefecto() throws Exception {
        when(categoriaService.listar(true)).thenReturn(List.of(Categoria.builder().id("c1").nombre("Hueco").activa(true).build()));

        mvc.perform(get("/api/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Hueco"));
        mvc.perform(get("/api/categorias").param("soloActivas", "false")).andExpect(status().isOk());

        verify(categoriaService).listar(false);
    }

    @Test
    void unaCategoriaRepetidaDevuelve409SinExponerElMensajeDelDriver() throws Exception {
        when(categoriaService.crear(any())).thenThrow(new DuplicateKeyException("E11000 duplicate key error collection: viasctg_db.categorias index: nombre_1"));

        mvc.perform(post("/api/categorias").header(HttpHeaders.AUTHORIZATION, bearer(Rol.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Hueco\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ya existe un registro con esos datos"))
                .andExpect(content().string(not(containsString("E11000"))));
    }

    @Test
    void confirmarUsaAlUsuarioDelTokenYDevuelve201() throws Exception {
        when(confirmacionService.crear("r1", "ciudadano")).thenReturn(Confirmacion.builder().id("f1").reporteId("r1")
                .usuarioId("ciudadano").fecha(Instant.parse("2026-10-22T08:00:00Z")).build());

        mvc.perform(post("/api/reportes/r1/confirmaciones").header(HttpHeaders.AUTHORIZATION, bearer(Rol.CIUDADANO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reporteId").value("r1"));
    }

    @Test
    void confirmarDosVecesDevuelve409() throws Exception {
        when(confirmacionService.crear("r1", "ciudadano")).thenThrow(new DuplicateResourceException("Ya confirmaste este reporte"));

        mvc.perform(post("/api/reportes/r1/confirmaciones").header(HttpHeaders.AUTHORIZATION, bearer(Rol.CIUDADANO)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ya confirmaste este reporte"));
    }

    @Test
    void retirarLaConfirmacionResponde204() throws Exception {
        mvc.perform(delete("/api/reportes/r1/confirmaciones/me").header(HttpHeaders.AUTHORIZATION, bearer(Rol.CIUDADANO)))
                .andExpect(status().isNoContent());

        verify(confirmacionService).retirar("r1", "ciudadano");
    }

    @Test
    void unMetodoNoAdmitidoDevuelve405() throws Exception {
        mvc.perform(delete("/api/categorias/c1").header(HttpHeaders.AUTHORIZATION, bearer(Rol.ADMIN)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("Esta dirección no admite el método DELETE"));
    }

    @Test
    void unErrorInesperadoDevuelve500SinDetallesTecnicos() throws Exception {
        when(barrioService.listar()).thenThrow(new IllegalStateException("NullPointer en BarrioService línea 28"));

        mvc.perform(get("/api/barrios"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Ocurrió un error interno. Inténtalo de nuevo en unos minutos"))
                .andExpect(content().string(not(containsString("BarrioService"))));
    }
}
