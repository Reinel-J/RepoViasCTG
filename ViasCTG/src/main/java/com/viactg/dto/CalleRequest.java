package com.viactg.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CalleRequest(@NotBlank(message = "El nombre de la calle es obligatorio") @Size(max = 150, message = "El nombre de la calle admite máximo 150 caracteres") String nombre,
                           @NotBlank(message = "El código postal es obligatorio") @Size(max = 20, message = "El código postal admite máximo 20 caracteres") String codigoPostal) {
}
