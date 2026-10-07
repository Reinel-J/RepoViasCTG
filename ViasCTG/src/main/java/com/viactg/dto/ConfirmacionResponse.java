package com.viactg.dto;

import java.time.Instant;

public record ConfirmacionResponse(String id, String reporteId, String usuarioId, Instant fecha) {
}
