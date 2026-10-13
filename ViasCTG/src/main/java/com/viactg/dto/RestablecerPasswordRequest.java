package com.viactg.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RestablecerPasswordRequest(@NotBlank(message = "El enlace de recuperación está incompleto") String token, @NotBlank(message = "La contraseña nueva es obligatoria") @Size(min = 8, max = 72, message = "La contraseña nueva debe tener entre 8 y 72 caracteres") String passwordNueva) {
}
