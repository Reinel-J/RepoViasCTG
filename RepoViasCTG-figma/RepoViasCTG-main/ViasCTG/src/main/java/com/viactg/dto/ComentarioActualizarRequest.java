package com.viactg.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ComentarioActualizarRequest(@NotBlank @Size(max = 1000) String texto) {
}
