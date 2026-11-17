package com.viactg.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BarrioRequest(
        @NotBlank(message = "El nombre del barrio es obligatorio") @Size(max = 100, message = "El nombre del barrio admite máximo 100 caracteres") String nombre,
        @NotBlank(message = "La localidad es obligatoria") @Size(max = 100, message = "La localidad admite máximo 100 caracteres") String localidad,
        List<@Valid CalleRequest> calles
) {
}
