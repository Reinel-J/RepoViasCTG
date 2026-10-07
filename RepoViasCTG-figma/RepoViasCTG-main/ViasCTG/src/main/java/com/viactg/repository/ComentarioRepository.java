package com.viactg.repository;

import com.viactg.model.Comentario;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ComentarioRepository extends MongoRepository<Comentario, String> {

    List<Comentario> findByReporteIdOrderByFechaAsc(String reporteId);
}
