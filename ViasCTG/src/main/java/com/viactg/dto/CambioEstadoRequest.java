package com.viactg.dto;

import com.viactg.model.EstadoReporte;
import com.viactg.model.PrioridadReporte;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CambioEstadoRequest(@NotNull EstadoReporte nuevoEstado,
                                  @Size(max = 500) String comentario,
                                  PrioridadReporte prioridad) {
}
