package com.viactg.dto;

import jakarta.validation.constraints.NotNull;

public record SeguimientoRequest(@NotNull(message = "Indica si el problema ya fue arreglado") Boolean arreglado) {
}
