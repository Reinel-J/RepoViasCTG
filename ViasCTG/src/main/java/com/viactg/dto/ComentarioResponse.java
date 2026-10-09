package com.viactg.dto;

import java.time.Instant;

public record ComentarioResponse(String id, String reporteId, String usuarioId, String usuarioNombre, String texto,
                                Instant fecha, boolean editado) {
}
