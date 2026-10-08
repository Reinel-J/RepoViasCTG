package com.viactg.dto;

import com.viactg.model.EstadoReporte;
import com.viactg.model.PrioridadReporte;
import java.time.Instant;
import java.util.List;

public record ReporteResponse(String id, String usuarioId, String calleId, String categoriaId, String direccionOsm,
                              String descripcion, String fotoUrl, Double latitud, Double longitud,
                              EstadoReporte estado, PrioridadReporte prioridad, Instant fechaCreacion,
                              Instant fechaActualizacion, List<HistorialEstadoResponse> historialEstados) {
}
