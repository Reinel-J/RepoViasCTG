package com.viactg.dto;

import jakarta.validation.constraints.NotBlank;

public record VerificarEmailRequest(@NotBlank(message = "El enlace de confirmación está incompleto") String token) {
}
