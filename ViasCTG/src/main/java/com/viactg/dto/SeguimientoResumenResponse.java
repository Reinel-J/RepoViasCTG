package com.viactg.dto;

import java.time.Instant;

/**
 * Votos de la comunidad sobre si el daño ya se arregló; {@code miRespuesta} es null si no votó o no inició sesión, y
 * {@code cierrePrevisto} es cuándo se cerrará solo por esos votos (null si no hay propuesta de cierre).
 */
public record SeguimientoResumenResponse(String reporteId, long arreglado, long sigue, Instant ultimaRespuesta,
                                         Boolean miRespuesta, Instant cierrePrevisto) {
}
