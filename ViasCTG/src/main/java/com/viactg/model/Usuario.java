package com.viactg.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document(collection = "usuarios")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    @Id
    private String id;

    private String nombre;

    @Indexed(unique = true)
    private String email;

    @Field("passwordHash")
    private String passwordHash;

    private String telefono;
    private Rol rol;
    private boolean activo;
    private Instant fechaRegistro;
    // Se incrementa para invalidar todos los JWT emitidos antes (cerrar sesiones, cambio de contraseña).
    private int versionToken;
}
