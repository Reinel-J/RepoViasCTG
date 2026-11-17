package com.viactg.repository;

import com.viactg.model.Rol;
import com.viactg.model.Usuario;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends MongoRepository<Usuario, String> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    List<Usuario> findByRolInAndActivoTrue(Collection<Rol> roles);
}
