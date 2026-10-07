package com.viactg.repository;

import com.viactg.model.EstadoReporte;
import com.viactg.model.Reporte;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ReporteRepository extends MongoRepository<Reporte, String> {

    List<Reporte> findByEstado(EstadoReporte estado);
}
