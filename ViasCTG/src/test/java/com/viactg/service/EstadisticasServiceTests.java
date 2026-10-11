package com.viactg.service;

import com.viactg.dto.EstadisticasResponse;
import com.viactg.model.EstadoReporte;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EstadisticasServiceTests {

    @Test
    void pipelineEsUnSoloFacetConTodasLasSubagregaciones() {
        List<Document> pipeline = EstadisticasService.pipeline(Instant.parse("2025-11-01T05:00:00Z"));

        assertThat(pipeline).hasSize(1);
        Document facet = pipeline.get(0).get("$facet", Document.class);
        assertThat(facet.keySet()).containsExactly("porEstado", "tiempos", "creadosPorMes", "resueltosPorMes", "categorias", "densidad");
        assertThat(facet.toString()).contains("America/Bogota", "$round", "RESUELTO");
    }

    @Test
    void aRespuestaRellenaMesesSinDatosYCalculaElPorcentajeSinContarRechazados() {
        Document resultado = new Document()
                .append("porEstado", List.of(grupo("PENDIENTE", 3), grupo("RESUELTO", 2), grupo("RECHAZADO", 5)))
                .append("tiempos", List.of(new Document("diasPrimeraRespuesta", 1.26).append("diasResolucion", null)))
                .append("creadosPorMes", List.of(grupo("2026-10", 4), grupo("2026-08", 1)))
                .append("resueltosPorMes", List.of(grupo("2026-10", 2)))
                .append("categorias", List.of(new Document("nombre", "Hueco").append("total", 6).append("resueltos", 2)))
                .append("densidad", List.of(new Document("_id", new Document("lat", 10.4).append("lng", -75.55)).append("total", 3)));

        EstadisticasResponse respuesta = EstadisticasService.aRespuesta(resultado, YearMonth.of(2025, 11));

        assertThat(respuesta.total()).isEqualTo(10);
        assertThat(respuesta.porEstado()).containsEntry(EstadoReporte.EN_PROCESO, 0L).containsEntry(EstadoReporte.RESUELTO, 2L);
        // 2 resueltos de 5 no rechazados.
        assertThat(respuesta.porcentajeResuelto()).isEqualTo(40);
        assertThat(respuesta.diasPrimeraRespuesta()).isEqualTo(1.3);
        assertThat(respuesta.diasResolucion()).isNull();
        assertThat(respuesta.meses()).hasSize(12);
        assertThat(respuesta.meses().get(0)).isEqualTo(new EstadisticasResponse.Mes("2025-11", 0, 0));
        assertThat(respuesta.meses().get(9)).isEqualTo(new EstadisticasResponse.Mes("2026-08", 1, 0));
        assertThat(respuesta.meses().get(11)).isEqualTo(new EstadisticasResponse.Mes("2026-10", 4, 2));
        assertThat(respuesta.categorias()).containsExactly(new EstadisticasResponse.Categoria("Hueco", 6, 2));
        assertThat(respuesta.densidad()).containsExactly(new EstadisticasResponse.CeldaDensidad(10.4, -75.55, 3));
    }

    @Test
    void aRespuestaSinReportesDevuelveCerosYNulos() {
        EstadisticasResponse respuesta = EstadisticasService.aRespuesta(new Document(), YearMonth.of(2025, 11));

        assertThat(respuesta.total()).isZero();
        assertThat(respuesta.porcentajeResuelto()).isNull();
        assertThat(respuesta.diasPrimeraRespuesta()).isNull();
        assertThat(respuesta.meses()).hasSize(12).allMatch(mes -> mes.creados() == 0 && mes.resueltos() == 0);
        assertThat(respuesta.categorias()).isEmpty();
        assertThat(respuesta.densidad()).isEmpty();
    }

    private Document grupo(String id, int total) {
        return new Document("_id", id).append("total", total);
    }
}
