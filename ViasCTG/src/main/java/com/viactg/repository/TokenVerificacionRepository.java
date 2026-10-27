package com.viactg.repository;

import com.viactg.model.TokenVerificacion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface TokenVerificacionRepository extends MongoRepository<TokenVerificacion, String> {
    Optional<TokenVerificacion> findByTokenHash(String tokenHash);

    void deleteByUsuarioId(String usuarioId);
}
