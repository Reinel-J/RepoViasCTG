package com.viactg.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UsuarioActualizacionRequest(
        @Size(min = 1, max = 100, message = "El nombre debe tener entre 1 y 100 caracteres") String nombre,
        @Pattern(regexp = "^[0-9+() -]{7,25}$", message = "El teléfono no tiene un formato válido") String telefono
) {
}
