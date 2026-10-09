package com.viactg.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Enlace de restablecimiento de contraseña. Solo se guarda el hash SHA-256 del token. */
@Document(collection = "tokens_recuperacion")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenRecuperacion {
    @Id
    private String id;
    private String usuarioId;
    @Indexed(unique = true)
    private String tokenHash;
    // Índice TTL: Mongo borra el documento al llegar a esta fecha.
    @Indexed(expireAfterSeconds = 0)
    private Instant expira;
}
