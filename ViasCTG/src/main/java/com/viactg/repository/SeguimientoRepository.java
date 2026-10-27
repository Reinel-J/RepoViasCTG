package com.viactg.repository;

import com.viactg.model.Seguimiento;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SeguimientoRepository extends MongoRepository<Seguimiento, String> {

    Optional<Seguimiento> findByReporteIdAndUsuarioId(String reporteId, String usuarioId);

    long countByReporteIdAndArreglado(String reporteId, boolean arreglado);

    Optional<Seguimiento> findFirstByReporteIdOrderByFechaDesc(String reporteId);

    List<Seguimiento> findByReporteId(String reporteId);
}
