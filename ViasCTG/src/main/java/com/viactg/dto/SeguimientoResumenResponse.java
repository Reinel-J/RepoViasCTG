package com.viactg.dto;

import java.time.Instant;

/**
 * Votos de la comunidad sobre si el daño ya se arregló; {@code arreglado} y {@code sigue} cuentan personas.
 * {@code miRespuesta} es null si no votó o no inició sesión, {@code cierrePrevisto} es cuándo se cerrará solo por esos
 * votos (null si no hay propuesta de cierre) y {@code progresoCierre}, los votos tal como los cuenta la regla del cierre
 * por la comunidad (solo los posteriores a EN_PROCESO, el del autor doble), o null si el cierre está apagado o el
 * reporte no está en proceso.
 */
public record SeguimientoResumenResponse(String reporteId, long arreglado, long sigue, Instant ultimaRespuesta,
                                         Boolean miRespuesta, Instant cierrePrevisto, ProgresoCierre progresoCierre) {
    public record ProgresoCierre(int arreglado, int sigue, int minimoVotos, double proporcionMinima) {
    }
}
