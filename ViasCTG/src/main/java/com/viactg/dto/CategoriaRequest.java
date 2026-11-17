package com.viactg.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoriaRequest(@NotBlank(message = "El nombre de la categoría es obligatorio") @Size(max = 100, message = "El nombre de la categoría admite máximo 100 caracteres") String nombre,
                               @Size(max = 500, message = "La descripción admite máximo 500 caracteres") String descripcion) {
}
