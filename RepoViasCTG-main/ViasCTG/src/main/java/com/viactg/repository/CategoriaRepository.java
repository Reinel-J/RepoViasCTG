package com.viactg.repository;

import com.viactg.model.Categoria;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CategoriaRepository extends MongoRepository<Categoria, String> {

    List<Categoria> findByActivaTrue();
}
