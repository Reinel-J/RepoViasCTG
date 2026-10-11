package com.viactg.service;

import com.viactg.dto.EstadisticasResponse;
import com.viactg.model.EstadoReporte;
import com.viactg.model.Reporte;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Estadísticas del panel calculadas en una sola agregación de MongoDB: {@code $facet} ejecuta varias
 * sub-agregaciones sobre la colección de reportes en un único recorrido.
 */
@Service
public class EstadisticasService {
    static final ZoneId ZONA = ZoneId.of("America/Bogota");
    static final int MESES = 12;
    static final int MAXIMO_CATEGORIAS = 8;
    static final int MAXIMO_CELDAS = 500;
    private static final double MILISEGUNDOS_POR_DIA = 86_400_000d;

    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    @Autowired
    public EstadisticasService(MongoTemplate mongoTemplate) {
        this(mongoTemplate, Clock.systemUTC());
    }

    EstadisticasService(MongoTemplate mongoTemplate, Clock clock) {
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
    }

    public EstadisticasResponse calcular() {
        YearMonth mesActual = YearMonth.now(clock.withZone(ZONA));
        YearMonth primerMes = mesActual.minusMonths(MESES - 1);
        Instant desde = primerMes.atDay(1).atStartOfDay(ZONA).toInstant();
        Document resultado = mongoTemplate.getCollection(mongoTemplate.getCollectionName(Reporte.class))
                .aggregate(pipeline(desde)).first();
        return aRespuesta(resultado == null ? new Document() : resultado, primerMes);
    }

    static List<Document> pipeline(Instant desde) {
        List<String> activos = EstadoReporte.ACTIVOS.stream().map(Enum::name).toList();
        Document mesDeCreacion = mes("$fechaCreacion");
        // El historial se agrega en orden con $push, así que su primer elemento es la primera respuesta del moderador.
        Document primeraRespuesta = new Document("$arrayElemAt", List.of("$historialEstados.fecha", 0));
        Document fechaResuelto = new Document("$getField", new Document("field", "fecha").append("input",
                new Document("$first", new Document("$filter", new Document("input", "$historialEstados")
                        .append("cond", new Document("$eq", List.of("$$this.estadoNuevo", "RESUELTO")))))));

        return List.of(new Document("$facet", new Document()
                .append("porEstado", List.of(
                        new Document("$group", new Document("_id", "$estado").append("total", new Document("$sum", 1)))))
                .append("tiempos", List.of(
                        new Document("$project", new Document("primera", dias("$fechaCreacion", primeraRespuesta))
                                .append("resolucion", dias("$fechaCreacion", fechaResuelto))),
                        // $avg ignora los null: reportes sin respuesta o sin resolver no cuentan en ese promedio.
                        new Document("$group", new Document("_id", null)
                                .append("diasPrimeraRespuesta", new Document("$avg", "$primera"))
                                .append("diasResolucion", new Document("$avg", "$resolucion")))))
                .append("creadosPorMes", List.of(
                        new Document("$match", new Document("fechaCreacion", new Document("$gte", desde))),
                        new Document("$group", new Document("_id", mesDeCreacion).append("total", new Document("$sum", 1)))))
                .append("resueltosPorMes", List.of(
                        new Document("$unwind", "$historialEstados"),
                        new Document("$match", new Document("historialEstados.estadoNuevo", "RESUELTO")
                                .append("historialEstados.fecha", new Document("$gte", desde))),
                        new Document("$group", new Document("_id", mes("$historialEstados.fecha")).append("total", new Document("$sum", 1)))))
                .append("categorias", List.of(
                        // La categoría es texto libre: "Hueco" y " hueco" se agrupan juntas.
                        new Document("$group", new Document("_id", new Document("$toLower", new Document("$trim", new Document("input", "$categoriaId"))))
                                .append("nombre", new Document("$first", new Document("$trim", new Document("input", "$categoriaId"))))
                                .append("total", new Document("$sum", 1))
                                .append("resueltos", new Document("$sum", new Document("$cond", List.of(new Document("$eq", List.of("$estado", "RESUELTO")), 1, 0))))),
                        new Document("$sort", new Document("total", -1).append("_id", 1)),
                        new Document("$limit", MAXIMO_CATEGORIAS)))
                .append("densidad", List.of(
                        new Document("$match", new Document("estado", new Document("$in", activos))),
                        // Redondear a 3 decimales agrupa los reportes en celdas de ~110 m de lado.
                        new Document("$group", new Document("_id", new Document("lat", new Document("$round", List.of("$latitud", 3)))
                                .append("lng", new Document("$round", List.of("$longitud", 3))))
                                .append("total", new Document("$sum", 1))),
                        new Document("$sort", new Document("total", -1)),
                        new Document("$limit", MAXIMO_CELDAS)))));
    }

    static EstadisticasResponse aRespuesta(Document resultado, YearMonth primerMes) {
        Map<EstadoReporte, Long> porEstado = new EnumMap<>(EstadoReporte.class);
        for (EstadoReporte estado : EstadoReporte.values()) porEstado.put(estado, 0L);
        for (Document grupo : lista(resultado, "porEstado")) {
            porEstado.put(EstadoReporte.valueOf(grupo.getString("_id")), numero(grupo, "total"));
        }
        long total = porEstado.values().stream().mapToLong(Long::longValue).sum();
        long validos = total - porEstado.get(EstadoReporte.RECHAZADO);
        Integer porcentajeResuelto = validos == 0 ? null : (int) Math.round(porEstado.get(EstadoReporte.RESUELTO) * 100.0 / validos);

        Document tiempos = lista(resultado, "tiempos").stream().findFirst().orElse(new Document());

        Map<String, Long> creados = porMes(lista(resultado, "creadosPorMes"));
        Map<String, Long> resueltos = porMes(lista(resultado, "resueltosPorMes"));
        List<EstadisticasResponse.Mes> meses = new ArrayList<>();
        for (int i = 0; i < MESES; i++) {
            String mes = primerMes.plusMonths(i).toString();
            meses.add(new EstadisticasResponse.Mes(mes, creados.getOrDefault(mes, 0L), resueltos.getOrDefault(mes, 0L)));
        }

        List<EstadisticasResponse.Categoria> categorias = lista(resultado, "categorias").stream()
                .map(grupo -> new EstadisticasResponse.Categoria(grupo.getString("nombre"), numero(grupo, "total"), numero(grupo, "resueltos")))
                .toList();
        List<EstadisticasResponse.CeldaDensidad> densidad = lista(resultado, "densidad").stream()
                .map(grupo -> {
                    Document celda = grupo.get("_id", Document.class);
                    return new EstadisticasResponse.CeldaDensidad(((Number) celda.get("lat")).doubleValue(),
                            ((Number) celda.get("lng")).doubleValue(), numero(grupo, "total"));
                })
                .toList();

        return new EstadisticasResponse(total, porEstado, porcentajeResuelto, redondear(tiempos.get("diasPrimeraRespuesta")),
                redondear(tiempos.get("diasResolucion")), meses, categorias, densidad);
    }

    private static Document mes(String campoFecha) {
        return new Document("$dateToString", new Document("format", "%Y-%m").append("date", campoFecha).append("timezone", ZONA.getId()));
    }

    private static Document dias(String desde, Object hasta) {
        return new Document("$divide", List.of(new Document("$subtract", List.of(hasta, desde)), MILISEGUNDOS_POR_DIA));
    }

    private static Map<String, Long> porMes(List<Document> grupos) {
        Map<String, Long> totales = new HashMap<>();
        grupos.forEach(grupo -> totales.put(grupo.getString("_id"), numero(grupo, "total")));
        return totales;
    }

    private static List<Document> lista(Document documento, String campo) {
        return documento.getList(campo, Document.class, List.of());
    }

    private static long numero(Document documento, String campo) {
        return ((Number) documento.get(campo)).longValue();
    }

    private static Double redondear(Object valor) {
        return valor instanceof Number numero ? Math.round(numero.doubleValue() * 10) / 10.0 : null;
    }
}
