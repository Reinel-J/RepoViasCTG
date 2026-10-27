package com.viactg.controller;

import com.viactg.dto.ReporteActualizarRequest;
import com.viactg.dto.ReporteCrearRequest;
import com.viactg.exception.BusinessRuleException;
import com.viactg.exception.ForbiddenOperationException;
import com.viactg.exception.ResourceNotFoundException;
import com.viactg.model.EstadoReporte;
import com.viactg.model.Rol;
import com.viactg.service.DuplicadoService;
import com.viactg.service.ReporteService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReporteControllerTests extends ControllerTestBase {
    private static final String REPORTE_VALIDO = """
            {"categoriaId":"hueco","descripcion":"Hueco profundo frente al colegio","latitud":10.42,"longitud":-75.54}""";

    @Test
    void listarPasaElEstadoYElLimiteAlServicio() throws Exception {
        when(reporteService.listar(EstadoReporte.EN_PROCESO, 10)).thenReturn(List.of(reporte("r1", "ciudadano")));

        mvc.perform(get("/api/reportes").param("estado", "EN_PROCESO").param("limite", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("r1"))
                .andExpect(jsonPath("$[0].estado").value("PENDIENTE"))
                .andExpect(jsonPath("$[0].fechaCreacion").value("2026-10-20T15:00:00Z"))
                .andExpect(jsonPath("$[0].fotos").isEmpty())
                .andExpect(jsonPath("$[0].ubicacion").doesNotExist());
    }

    @Test
    void sinFiltrosListaTodos() throws Exception {
        mvc.perform(get("/api/reportes")).andExpect(status().isOk());

        verify(reporteService).listar(isNull(), isNull());
    }

    @Test
    void unLimiteFueraDeRangoDevuelve400() throws Exception {
        mvc.perform(get("/api/reportes").param("limite", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La solicitud contiene parámetros inválidos"))
                .andExpect(jsonPath("$.validationErrors['listar.limite']").value("El límite debe ser al menos 1"));
        mvc.perform(get("/api/reportes").param("limite", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors['listar.limite']").value("El límite no puede superar 100"));

        verifyNoInteractions(reporteService);
    }

    @Test
    void unEstadoDesconocidoDevuelve400() throws Exception {
        mvc.perform(get("/api/reportes").param("estado", "ARREGLADO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El parámetro 'estado' tiene un valor no válido"));
    }

    @Test
    void resumenDevuelveLasCifrasDeLaPortada() throws Exception {
        when(reporteService.calcularResumen(any())).thenReturn(new ReporteService.Resumen(12,
                Map.of(EstadoReporte.PENDIENTE, 7L, EstadoReporte.RESUELTO, 5L), 3, 4));

        mvc.perform(get("/api/reportes/resumen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(12))
                .andExpect(jsonPath("$.porEstado.PENDIENTE").value(7))
                .andExpect(jsonPath("$.creadosUltimas24Horas").value(3))
                .andExpect(jsonPath("$.barrios").value(4));
    }

    @Test
    void cercanosUsaUnRadioDe2KmPorDefecto() throws Exception {
        mvc.perform(get("/api/reportes/cercanos").param("lat", "10.42").param("lng", "-75.54"))
                .andExpect(status().isOk());

        verify(reporteService).listarCercanos(10.42, -75.54, 2.0, null);
    }

    @Test
    void cercanosValidaLasCoordenadasYElRadio() throws Exception {
        mvc.perform(get("/api/reportes/cercanos").param("lng", "-75.54"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Falta el parámetro obligatorio 'lat'"));
        mvc.perform(get("/api/reportes/cercanos").param("lat", "91").param("lng", "-75.54").param("radioKm", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors['listarCercanos.lat']").value("La latitud debe estar entre -90 y 90"))
                .andExpect(jsonPath("$.validationErrors['listarCercanos.radioKm']").value("El radio no puede superar 50 km"));
        mvc.perform(get("/api/reportes/cercanos").param("lat", "norte").param("lng", "-75.54"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El parámetro 'lat' tiene un valor no válido"));

        verifyNoInteractions(reporteService);
    }

    @Test
    void posiblesDuplicadosIncluyeDistanciaYConfirmaciones() throws Exception {
        when(duplicadoService.buscar(10.42, -75.54))
                .thenReturn(List.of(new DuplicadoService.PosibleDuplicado(reporte("r9", "otro"), 35, 4)));

        mvc.perform(get("/api/reportes/posibles-duplicados").param("lat", "10.42").param("lng", "-75.54"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reporte.id").value("r9"))
                .andExpect(jsonPath("$[0].distanciaMetros").value(35))
                .andExpect(jsonPath("$[0].confirmaciones").value(4));
    }

    @Test
    void unReporteInexistenteDevuelve404ConElMensajeDelServicio() throws Exception {
        when(reporteService.buscarPorId("nada")).thenThrow(new ResourceNotFoundException("Reporte no encontrado"));

        mvc.perform(get("/api/reportes/nada"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Reporte no encontrado"));
    }

    @Test
    void misReportesUsaElUsuarioDelToken() throws Exception {
        when(reporteService.listarDeUsuario("ciudadano", EstadoReporte.RESUELTO)).thenReturn(List.of(reporte("r1", "ciudadano")));

        mvc.perform(get("/api/reportes/mios").param("estado", "RESUELTO").header(HttpHeaders.AUTHORIZATION, bearer(Rol.CIUDADANO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].usuarioId").value("ciudadano"));
    }

    @Test
    void crearAsignaElReporteAlUsuarioDelTokenYNoAlDelCuerpo() throws Exception {
        when(reporteService.crear(any(), eq("ciudadano"))).thenReturn(reporte("r1", "ciudadano"));

        mvc.perform(post("/api/reportes").header(HttpHeaders.AUTHORIZATION, bearer(Rol.CIUDADANO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REPORTE_VALIDO.replace("{", "{\"usuarioId\":\"otra-persona\",\"estado\":\"RESUELTO\",")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("r1"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.direccionOsm").isEmpty());

        ArgumentCaptor<ReporteCrearRequest> captor = ArgumentCaptor.forClass(ReporteCrearRequest.class);
        verify(reporteService).crear(captor.capture(), eq("ciudadano"));
        assertThat(captor.getValue()).isEqualTo(new ReporteCrearRequest("hueco", "Hueco profundo frente al colegio", 10.42, -75.54));
    }

    @Test
    void crearConDatosInvalidosDevuelve400() throws Exception {
        mvc.perform(post("/api/reportes").header(HttpHeaders.AUTHORIZATION, bearer(Rol.CIUDADANO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"corta\",\"latitud\":95,\"longitud\":-75.54}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.categoriaId").value("La categoría es obligatoria"))
                .andExpect(jsonPath("$.validationErrors.descripcion").value("La descripción debe tener entre 10 y 2000 caracteres"))
                .andExpect(jsonPath("$.validationErrors.latitud").value("La latitud debe ser menor o igual a 90"));

        verifyNoInteractions(reporteService);
    }

    @Test
    void editarUnReporteYaRevisadoDevuelve409() throws Exception {
        when(reporteService.actualizar(eq("r1"), any(), eq("ciudadano")))
                .thenThrow(new BusinessRuleException("Solo se pueden editar reportes pendientes"));

        mvc.perform(put("/api/reportes/r1").header(HttpHeaders.AUTHORIZATION, bearer(Rol.CIUDADANO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"Hueco profundo y más grande\",\"latitud\":10.42,\"longitud\":-75.54}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Solo se pueden editar reportes pendientes"));

        verify(reporteService).actualizar("r1", new ReporteActualizarRequest("Hueco profundo y más grande", 10.42, -75.54), "ciudadano");
    }

    @Test
    void editarElReporteDeOtraPersonaDevuelve403() throws Exception {
        when(reporteService.actualizar(eq("r1"), any(), eq("ciudadano")))
                .thenThrow(new ForbiddenOperationException("Solo el autor puede editar este reporte"));

        mvc.perform(put("/api/reportes/r1").header(HttpHeaders.AUTHORIZATION, bearer(Rol.CIUDADANO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"Hueco profundo y más grande\",\"latitud\":10.42,\"longitud\":-75.54}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Solo el autor puede editar este reporte"));
    }

    @Test
    void subirFotosEntregaLosArchivosAlServicio() throws Exception {
        when(reporteService.subirFotos(eq("r1"), anyList(), eq("ciudadano"))).thenReturn(reporte("r1", "ciudadano"));

        mvc.perform(multipart("/api/reportes/r1/fotos")
                        .file(new MockMultipartFile("archivos", "uno.jpg", "image/jpeg", new byte[]{1, 2, 3}))
                        .file(new MockMultipartFile("archivos", "dos.png", "image/png", new byte[]{4, 5}))
                        .header(HttpHeaders.AUTHORIZATION, bearer(Rol.CIUDADANO)))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MultipartFile>> captor = ArgumentCaptor.forClass(List.class);
        verify(reporteService).subirFotos(eq("r1"), captor.capture(), eq("ciudadano"));
        assertThat(captor.getValue()).extracting(MultipartFile::getOriginalFilename).containsExactly("uno.jpg", "dos.png");
    }

    @Test
    void subirFotosSinArchivosDevuelve400() throws Exception {
        mvc.perform(multipart("/api/reportes/r1/fotos").header(HttpHeaders.AUTHORIZATION, bearer(Rol.CIUDADANO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Falta el archivo 'archivos' en la solicitud"));

        verifyNoInteractions(reporteService);
    }

    @Test
    void subirFotosComoJsonDevuelve415() throws Exception {
        mvc.perform(post("/api/reportes/r1/fotos").header(HttpHeaders.AUTHORIZATION, bearer(Rol.CIUDADANO))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType());
    }
}
