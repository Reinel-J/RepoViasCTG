package com.viactg.config;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.viactg.model.Categoria;
import com.viactg.model.Reporte;
import com.viactg.service.CategoriaService;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Antes, el reporte guardaba la categoría como texto libre en {@code categoriaId} («Hueco», «hueco », «bache»…). Ahora la
 * embebe como {@code categoria: {id, nombre}} con una categoría del catálogo. Esta migración convierte los reportes
 * antiguos: el texto que coincide con una categoría (sin distinguir mayúsculas, tildes ni espacios) queda con ella, y el
 * que no coincide con ninguna, con «Otro». Solo toca reportes que aún tienen {@code categoriaId}, así que en los
 * arranques siguientes no hace nada.
 * <p>
 * Trabaja directamente sobre la colección, sin el mapeo de {@link Reporte}: Spring Data leería {@code categoriaId} como la
 * ruta {@code categoria.id} (parte el nombre en camelCase) y nunca encontraría el campo antiguo.
 */
@Component
// Después de CategoriasIniciales (el catálogo debe existir) y antes de DatosDemo.
@Order(1)
public class MigracionCategoriaReporte implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(MigracionCategoriaReporte.class);
    static final String CAMPO_ANTIGUO = "categoriaId";

    private final MongoTemplate mongoTemplate;
    private final CategoriaService categoriaService;

    public MigracionCategoriaReporte(MongoTemplate mongoTemplate, CategoriaService categoriaService) {
        this.mongoTemplate = mongoTemplate;
        this.categoriaService = categoriaService;
    }

    @Override
    public void run(ApplicationArguments args) {
        MongoCollection<Document> reportes = mongoTemplate.getCollection(mongoTemplate.getCollectionName(Reporte.class));
        Bson conCampoAntiguo = Filters.exists(CAMPO_ANTIGUO);
        List<String> textos = reportes.distinct(CAMPO_ANTIGUO, conCampoAntiguo, String.class).into(new ArrayList<>());
        long migrados = 0;
        long aOtro = 0;
        for (String texto : textos) {
            if (texto == null) continue;
            Optional<Categoria> encontrada = texto.isBlank() ? Optional.empty() : categoriaService.buscarPorNombre(texto);
            Categoria categoria = encontrada.orElseGet(categoriaService::asegurarOtro);
            long cambiados = migrar(reportes, Filters.eq(CAMPO_ANTIGUO, texto), categoria);
            migrados += cambiados;
            if (encontrada.isEmpty() && cambiados > 0) {
                aOtro += cambiados;
                log.info("Categoría antigua «{}» sin equivalente en el catálogo: {} reporte(s) quedaron en «{}»", texto, cambiados,
                        categoria.getNombre());
            }
        }
        // Quedan los que tenían el campo antiguo en null.
        if (reportes.countDocuments(conCampoAntiguo) > 0) {
            long sinTexto = migrar(reportes, conCampoAntiguo, categoriaService.asegurarOtro());
            migrados += sinTexto;
            aOtro += sinTexto;
        }
        if (migrados > 0) {
            log.info("Migración de categorías: {} reporte(s) pasaron a la categoría embebida ({} en «Otro»)", migrados, aOtro);
        }
    }

    // Mismo formato que escribe Spring Data para Reporte.categoria (ver CategoriaReporte): {id, nombre}.
    private static long migrar(MongoCollection<Document> reportes, Bson filtro, Categoria categoria) {
        Document embebida = new Document("id", categoria.getId()).append("nombre", categoria.getNombre());
        return reportes.updateMany(Filters.and(filtro, Filters.exists(CAMPO_ANTIGUO)),
                Updates.combine(Updates.set("categoria", embebida), Updates.unset(CAMPO_ANTIGUO))).getModifiedCount();
    }
}
