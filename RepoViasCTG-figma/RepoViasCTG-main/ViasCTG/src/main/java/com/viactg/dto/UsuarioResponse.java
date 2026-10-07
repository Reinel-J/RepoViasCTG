package com.viactg.dto;

import com.viactg.model.Rol;
import java.time.Instant;

public record UsuarioResponse(String id, String nombre, String email, String telefono,
                              Rol rol, boolean activo, Instant fechaRegistro) {
}
