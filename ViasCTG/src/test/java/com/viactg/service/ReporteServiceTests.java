package com.viactg.service;

import com.viactg.model.EstadoReporte;
import com.viactg.model.Reporte;
import com.viactg.repository.ReporteRepository;
import com.viactg.repository.UsuarioRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReporteServiceTests {

    @Test
    void listarCercanosConstruyeLaConsultaGeoespacialYDevuelveElResultadoDeMongo() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        Reporte esperado = Reporte.builder().id("reporte-1").build();
        when(mongoTemplate.find(any(Query.class), eq(Reporte.class))).thenReturn(List.of(esperado));
        ReporteService service = crearServicio(mongoTemplate);

        List<Reporte> resultado = service.listarCercanos(10.391, -75.479, 2, EstadoReporte.PENDIENTE);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(Reporte.class));
        Document consulta = queryCaptor.getValue().getQueryObject();
        assertThat(resultado).containsExactly(esperado);
        assertThat(consulta.toString()).contains("$nearSphere", "$maxDistance", "-75.479", "10.391", "PENDIENTE");
    }

    @Test
    void listarCercanosLanzaExcepcionCuandoElRadioNoEsPositivo() {
        ReporteService service = crearServicio(mock(MongoTemplate.class));

        assertThatThrownBy(() -> service.listarCercanos(10.391, -75.479, 0, null))
                .isInstanceOf(com.viactg.exception.BusinessRuleException.class)
                .hasMessage("El radio de búsqueda debe ser mayor a cero");
        assertThatThrownBy(() -> service.listarCercanos(10.391, -75.479, -1, null))
                .isInstanceOf(com.viactg.exception.BusinessRuleException.class)
                .hasMessage("El radio de búsqueda debe ser mayor a cero");
    }

    @Test
    void cambiarEstadoInsertaHistorialEnLaActualizacionAtomica() {
        ReporteRepository reporteRepository = mock(ReporteRepository.class);
        UsuarioService usuarioService = mock(UsuarioService.class);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        Reporte reporte = Reporte.builder().id("reporte-1").estado(EstadoReporte.PENDIENTE)
                .fechaCreacion(Instant.now()).fechaActualizacion(Instant.now()).build();
        Reporte actualizado = Reporte.builder().id("reporte-1").estado(EstadoReporte.EN_REVISION)
                .historialEstados(java.util.List.of()).build();
        when(usuarioService.esAdministradorOModerador("admin-1")).thenReturn(true);
        when(reporteRepository.findById("reporte-1")).thenReturn(Optional.of(reporte));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Reporte.class)))
                .thenReturn(actualizado);

        Reporte resultado = new ReporteService(reporteRepository, mock(UsuarioRepository.class), usuarioService,
                mongoTemplate, mock(GeocodingService.class), "/tmp/viasctg-test-uploads")
                .cambiarEstadoReporte("reporte-1", EstadoReporte.EN_REVISION, "admin-1", "En revisión", null);

        assertThat(resultado.getEstado()).isEqualTo(EstadoReporte.EN_REVISION);
    }

    private ReporteService crearServicio(MongoTemplate mongoTemplate) {
        return new ReporteService(mock(ReporteRepository.class), mock(UsuarioRepository.class), mock(UsuarioService.class),
                mongoTemplate, mock(GeocodingService.class), "/tmp/viasctg-test-uploads");
    }
}
