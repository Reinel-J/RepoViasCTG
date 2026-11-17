package com.viactg.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ComentarioCrearRequest(@NotBlank(message = "El comentario no puede estar vacío") @Size(max = 1000, message = "El comentario admite máximo 1000 caracteres") String texto) {
}
