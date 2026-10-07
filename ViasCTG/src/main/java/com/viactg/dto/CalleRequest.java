package com.viactg.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CalleRequest(@NotBlank @Size(max = 150) String nombre,
                           @NotBlank @Size(max = 20) String codigoPostal) {
}
