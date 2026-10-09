package com.viactg.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CambioPasswordRequest(@NotBlank String passwordActual, @NotBlank @Size(min = 8, max = 72) String passwordNueva) {
}
