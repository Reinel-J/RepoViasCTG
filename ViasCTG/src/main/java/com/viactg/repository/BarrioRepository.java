package com.viactg.repository;

import com.viactg.model.Barrio;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface BarrioRepository extends MongoRepository<Barrio, String> {

    Optional<Barrio> findByCallesId(String calleId);
}
