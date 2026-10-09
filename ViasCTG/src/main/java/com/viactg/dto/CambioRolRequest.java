package com.viactg.dto;

import com.viactg.model.Rol;
import jakarta.validation.constraints.NotNull;

public record CambioRolRequest(@NotNull Rol rol) {
}
