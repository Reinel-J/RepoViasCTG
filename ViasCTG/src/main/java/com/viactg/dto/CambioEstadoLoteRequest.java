package com.viactg.dto;

import com.viactg.model.EstadoReporte;
import com.viactg.model.PrioridadReporte;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CambioEstadoLoteRequest(
        @NotEmpty(message = "Selecciona al menos un reporte") @Size(max = 50, message = "Puedes actualizar como máximo 50 reportes a la vez") List<String> ids,
        @NotNull(message = "El nuevo estado es obligatorio") EstadoReporte nuevoEstado,
        @Size(max = 500, message = "El comentario admite máximo 500 caracteres") String comentario,
        PrioridadReporte prioridad) {
}
