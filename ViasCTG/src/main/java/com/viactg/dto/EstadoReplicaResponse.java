package com.viactg.dto;

import java.time.Instant;
import java.util.List;

/**
 * Estado de la réplica SQL para el panel: si está habilitada y conectada, cuándo se copió algo por última vez, el último
 * error y, por tabla, cuántos registros hay en MongoDB y cuántas filas en PostgreSQL ({@code sql} null si no responde).
 */
public record EstadoReplicaResponse(boolean habilitada, boolean conectada, boolean pendienteReconstruir,
                                    Instant ultimaSincronizacion, Instant ultimaReconstruccion, String ultimoError,
                                    Instant ultimoErrorEn, List<Tabla> tablas) {

    public record Tabla(String nombre, long mongo, Long sql) {
    }
}
