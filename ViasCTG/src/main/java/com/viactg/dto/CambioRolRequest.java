package com.viactg.dto;

import com.viactg.model.Rol;
import jakarta.validation.constraints.NotNull;

public record CambioRolRequest(@NotNull(message = "El rol es obligatorio") Rol rol) {
}
