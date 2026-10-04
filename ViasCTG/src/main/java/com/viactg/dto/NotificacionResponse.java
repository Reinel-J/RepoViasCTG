package com.viactg.dto;

import java.time.Instant;

public record NotificacionResponse(String id, String usuarioId, String reporteId, String mensaje,
                                   boolean leida, Instant fecha) {
}
