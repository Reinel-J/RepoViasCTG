package com.viactg.dto;

import com.viactg.model.EstadoReporte;
import java.time.Instant;

public record HistorialEstadoResponse(String id, String usuarioAdminId, EstadoReporte estadoAnterior,
                                      EstadoReporte estadoNuevo, String comentarioAdmin, Instant fecha) {
}
