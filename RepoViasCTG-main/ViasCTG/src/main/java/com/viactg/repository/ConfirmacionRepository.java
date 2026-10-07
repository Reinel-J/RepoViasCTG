package com.viactg.repository;

import com.viactg.model.Confirmacion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ConfirmacionRepository extends MongoRepository<Confirmacion, String> {

    boolean existsByReporteIdAndUsuarioId(String reporteId, String usuarioId);

    List<Confirmacion> findByReporteId(String reporteId);

    java.util.Optional<Confirmacion> findByReporteIdAndUsuarioId(String reporteId, String usuarioId);
}
