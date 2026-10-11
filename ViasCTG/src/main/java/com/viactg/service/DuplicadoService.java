package com.viactg.service;

import com.viactg.model.EstadoReporte;
import com.viactg.model.Reporte;
import com.viactg.repository.ConfirmacionRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

/** Detecta reportes activos muy cercanos a un punto para que el mismo daño no se reporte varias veces. */
@Service
public class DuplicadoService {
    static final double RADIO_METROS = 100;
    static final int MAXIMO_RESULTADOS = 5;
    private static final double RADIO_TIERRA_METROS = 6_371_000;

    private final MongoTemplate mongoTemplate;
    private final ConfirmacionRepository confirmacionRepository;

    public DuplicadoService(MongoTemplate mongoTemplate, ConfirmacionRepository confirmacionRepository) {
        this.mongoTemplate = mongoTemplate;
        this.confirmacionRepository = confirmacionRepository;
    }

    /** Devuelve los reportes activos dentro del radio, del más cercano al más lejano. */
    public List<PosibleDuplicado> buscar(double latitud, double longitud) {
        Criteria criteria = Criteria.where("ubicacion").nearSphere(new GeoJsonPoint(longitud, latitud)).maxDistance(RADIO_METROS)
                .and("estado").in(EstadoReporte.ACTIVOS);
        return mongoTemplate.find(Query.query(criteria).limit(MAXIMO_RESULTADOS), Reporte.class).stream()
                .map(reporte -> new PosibleDuplicado(reporte,
                        Math.round(distanciaMetros(latitud, longitud, reporte.getLatitud(), reporte.getLongitud())),
                        confirmacionRepository.countByReporteId(reporte.getId())))
                .toList();
    }

    // Fórmula de haversine: distancia sobre la superficie terrestre entre dos coordenadas.
    static double distanciaMetros(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * RADIO_TIERRA_METROS * Math.asin(Math.sqrt(a));
    }

    public record PosibleDuplicado(Reporte reporte, long distanciaMetros, long confirmaciones) {
    }
}
