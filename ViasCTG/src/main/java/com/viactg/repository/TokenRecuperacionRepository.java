package com.viactg.repository;

import com.viactg.model.TokenRecuperacion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface TokenRecuperacionRepository extends MongoRepository<TokenRecuperacion, String> {
    Optional<TokenRecuperacion> findByTokenHash(String tokenHash);

    void deleteByUsuarioId(String usuarioId);
}
