package com.viactg.model;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.convert.QueryMapper;
import org.springframework.data.mongodb.core.convert.UpdateMapper;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cómo queda la categoría embebida en MongoDB, con el conversor real de Spring Data (sin base de datos). Las
 * agregaciones de estadísticas, Metabase y los scripts leen {@code categoria.id} tal cual, así que no puede guardarse
 * como {@code _id}, que es lo que Spring hace por defecto con un campo "id" anidado.
 */
class CategoriaReporteMapeoTests {
    private MongoMappingContext contexto;
    private MappingMongoConverter conversor;
    private MongoPersistentEntity<?> reporte;

    @BeforeEach
    void conversor() {
        // Igual que la configuración de Spring Boot: Instant y los demás tipos de fecha son simples, no entidades.
        MongoCustomConversions conversiones = new MongoCustomConversions(List.of());
        contexto = new MongoMappingContext();
        contexto.setSimpleTypeHolder(conversiones.getSimpleTypeHolder());
        contexto.afterPropertiesSet();
        conversor = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, contexto);
        conversor.setCustomConversions(conversiones);
        conversor.afterPropertiesSet();
        reporte = contexto.getRequiredPersistentEntity(Reporte.class);
    }

    @Test
    void seGuardaComoIdYNombreSinTipo() {
        Document documento = new Document();

        conversor.write(Reporte.builder().id("r1").categoria(new CategoriaReporte("c1", "Hueco")).build(), documento);

        assertThat(documento.get("categoria")).isEqualTo(new Document("id", "c1").append("nombre", "Hueco"));
        assertThat(documento).doesNotContainKey("categoriaId");
    }

    @Test
    void seLeeDeVueltaDesdeElDocumento() {
        Document documento = new Document("_id", "r1").append("categoria", new Document("id", "c1").append("nombre", "Hueco"));

        Reporte leido = conversor.read(Reporte.class, documento);

        assertThat(leido.getCategoria().getId()).isEqualTo("c1");
        assertThat(leido.getCategoria().getNombre()).isEqualTo("Hueco");
    }

    @Test
    void lasConsultasYActualizacionesUsanCategoriaPuntoId() {
        Document consulta = new QueryMapper(conversor).getMappedObject(new Document("categoria.id", "c1"), reporte);
        Document cambio = new UpdateMapper(conversor).getMappedObject(
                new Update().set("categoria", new CategoriaReporte("c1", "Hueco")).getUpdateObject(), reporte);

        assertThat(consulta).isEqualTo(new Document("categoria.id", "c1"));
        assertThat(cambio).isEqualTo(new Document("$set", new Document("categoria", new Document("id", "c1").append("nombre", "Hueco"))));
    }
}
