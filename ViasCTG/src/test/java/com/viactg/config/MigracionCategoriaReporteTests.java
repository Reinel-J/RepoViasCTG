package com.viactg.config;

import com.mongodb.client.DistinctIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.result.UpdateResult;
import com.viactg.model.Categoria;
import com.viactg.model.Reporte;
import com.viactg.service.CategoriaService;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MigracionCategoriaReporteTests {
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    @SuppressWarnings("unchecked")
    private final MongoCollection<Document> reportes = mock(MongoCollection.class);
    @SuppressWarnings("unchecked")
    private final DistinctIterable<String> distintos = mock(DistinctIterable.class);
    private final CategoriaService categoriaService = mock(CategoriaService.class);
    private final MigracionCategoriaReporte migracion = new MigracionCategoriaReporte(mongoTemplate, categoriaService);

    private static final Categoria HUECO = Categoria.builder().id("c-hueco").nombre("Hueco").activa(true).build();
    private static final Categoria OTRO = Categoria.builder().id("c-otro").nombre("Otro").activa(true).build();

    @BeforeEach
    void coleccion() {
        when(mongoTemplate.getCollectionName(Reporte.class)).thenReturn("reportes");
        when(mongoTemplate.getCollection("reportes")).thenReturn(reportes);
        when(reportes.distinct(eq("categoriaId"), any(Bson.class), eq(String.class))).thenReturn(distintos);
        when(reportes.updateMany(any(Bson.class), any(Bson.class))).thenReturn(UpdateResult.acknowledged(2, 2L, null));
    }

    @SuppressWarnings("unchecked")
    private void textosAntiguos(String... textos) {
        when(distintos.into(any())).thenAnswer(invocacion -> {
            Collection<String> destino = invocacion.getArgument(0);
            destino.addAll(Arrays.asList(textos));
            return destino;
        });
    }

    private static BsonDocument bson(Bson valor) {
        return valor.toBsonDocument(Document.class, com.mongodb.MongoClientSettings.getDefaultCodecRegistry());
    }

    @Test
    void elTextoQueCoincideConElCatalogoQuedaConEsaCategoriaYElQueNoConOtro() {
        textosAntiguos(" hueco ", "Semáforo dañado");
        when(categoriaService.buscarPorNombre(" hueco ")).thenReturn(Optional.of(HUECO));
        when(categoriaService.buscarPorNombre("Semáforo dañado")).thenReturn(Optional.empty());
        when(categoriaService.asegurarOtro()).thenReturn(OTRO);

        migracion.run(null);

        ArgumentCaptor<Bson> filtros = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<Bson> cambios = ArgumentCaptor.forClass(Bson.class);
        verify(reportes, times(2)).updateMany(filtros.capture(), cambios.capture());
        assertThat(bson(filtros.getAllValues().get(0)).toJson()).contains("\"categoriaId\": \" hueco \"", "$exists");
        assertThat(bson(filtros.getAllValues().get(1)).toJson()).contains("\"categoriaId\": \"Semáforo dañado\"");
        BsonDocument primero = bson(cambios.getAllValues().get(0));
        assertThat(primero.getDocument("$set").getDocument("categoria").toJson()).isEqualTo("{\"id\": \"c-hueco\", \"nombre\": \"Hueco\"}");
        assertThat(primero.getDocument("$unset").containsKey("categoriaId")).isTrue();
        assertThat(bson(cambios.getAllValues().get(1)).getDocument("$set").getDocument("categoria").getString("nombre").getValue())
                .isEqualTo("Otro");
    }

    @Test
    void sinReportesAntiguosNoHaceNadaNiCreaOtro() {
        textosAntiguos();

        migracion.run(null);

        verify(reportes, never()).updateMany(any(Bson.class), any(Bson.class));
        verify(categoriaService, never()).asegurarOtro();
        verify(categoriaService, never()).buscarPorNombre(anyString());
    }

    @Test
    void losReportesConLaCategoriaAntiguaNulaQuedanEnOtro() {
        textosAntiguos((String) null);
        when(reportes.countDocuments(any(Bson.class))).thenReturn(1L);
        when(categoriaService.asegurarOtro()).thenReturn(OTRO);

        migracion.run(null);

        ArgumentCaptor<Bson> cambio = ArgumentCaptor.forClass(Bson.class);
        verify(reportes).updateMany(any(Bson.class), cambio.capture());
        assertThat(bson(cambio.getValue()).getDocument("$set").getDocument("categoria").getString("id").getValue()).isEqualTo("c-otro");
        verify(categoriaService, never()).buscarPorNombre(anyString());
    }

    @Test
    void elTextoVacioTambienQuedaEnOtro() {
        textosAntiguos("  ");
        when(categoriaService.asegurarOtro()).thenReturn(OTRO);

        migracion.run(null);

        verify(reportes).updateMany(any(Bson.class), any(Bson.class));
        verify(categoriaService, never()).buscarPorNombre(anyString());
    }
}
