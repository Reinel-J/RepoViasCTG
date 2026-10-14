package com.viactg.repository;

import com.viactg.model.EstadoReporte;
import com.viactg.model.Reporte;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;

public interface ReporteRepository extends MongoRepository<Reporte, String> {
    long countByEstado(EstadoReporte estado);

    long countByFechaCreacionGreaterThanEqual(Instant desde);
}
