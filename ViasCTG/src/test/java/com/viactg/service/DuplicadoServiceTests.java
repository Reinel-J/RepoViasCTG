package com.viactg.service;

import com.viactg.model.Reporte;
import com.viactg.repository.ConfirmacionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DuplicadoServiceTests {

    @Test
    void buscarConsultaSoloReportesActivosDentroDelRadioYLimitaResultados() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.find(any(Query.class), eq(Reporte.class))).thenReturn(List.of());

        new DuplicadoService(mongoTemplate, mock(ConfirmacionRepository.class)).buscar(10.391, -75.479);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(Reporte.class));
        Query query = queryCaptor.getValue();
        assertThat(query.getQueryObject().toString())
                .contains("$nearSphere", "$maxDistance=100.0", "PENDIENTE", "EN_REVISION", "EN_PROCESO")
                .doesNotContain("RESUELTO", "RECHAZADO");
        assertThat(query.getLimit()).isEqualTo(DuplicadoService.MAXIMO_RESULTADOS);
    }

    @Test
    void buscarCalculaDistanciaYCuentaConfirmaciones() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        ConfirmacionRepository confirmacionRepository = mock(ConfirmacionRepository.class);
        // Unos 55 m al norte del punto consultado.
        Reporte cercano = Reporte.builder().id("reporte-1").latitud(10.3915).longitud(-75.479).build();
        when(mongoTemplate.find(any(Query.class), eq(Reporte.class))).thenReturn(List.of(cercano));
        when(confirmacionRepository.countByReporteId("reporte-1")).thenReturn(4L);

        List<DuplicadoService.PosibleDuplicado> resultado =
                new DuplicadoService(mongoTemplate, confirmacionRepository).buscar(10.391, -75.479);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).reporte()).isSameAs(cercano);
        assertThat(resultado.get(0).distanciaMetros()).isBetween(54L, 57L);
        assertThat(resultado.get(0).confirmaciones()).isEqualTo(4);
    }

    @Test
    void distanciaMetrosCoincideConUnaDistanciaConocida() {
        // Una centésima de grado de latitud equivale a unos 1.112 m en cualquier parte del planeta.
        double distancia = DuplicadoService.distanciaMetros(10.39, -75.48, 10.40, -75.48);
        assertThat(distancia).isCloseTo(1112, within(2.0));
    }
}
