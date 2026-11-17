package com.viactg.dto;

import com.viactg.model.EstadoReporte;
import com.viactg.model.PrioridadReporte;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CambioEstadoRequest(@NotNull(message = "El nuevo estado es obligatorio") EstadoReporte nuevoEstado,
                                  @Size(max = 500, message = "El comentario admite máximo 500 caracteres") String comentario,
                                  PrioridadReporte prioridad) {
}
