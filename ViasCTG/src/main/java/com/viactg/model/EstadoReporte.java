package com.viactg.model;

import java.util.List;

public enum EstadoReporte {
    PENDIENTE,
    EN_REVISION,
    EN_PROCESO,
    RESUELTO,
    RECHAZADO;

    /** Estados en los que el daño sigue abierto: ni resuelto ni rechazado. */
    public static final List<EstadoReporte> ACTIVOS = List.of(PENDIENTE, EN_REVISION, EN_PROCESO);

    public boolean esActivo() {
        return ACTIVOS.contains(this);
    }
}
