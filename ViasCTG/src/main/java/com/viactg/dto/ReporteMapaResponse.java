package com.viactg.dto;

import com.viactg.model.EstadoReporte;

/** Lo que necesita el mapa público de cada reporte: sin historial ni datos del autor. {@code foto} es la primera o null. */
public record ReporteMapaResponse(String id, Double latitud, Double longitud, EstadoReporte estado, String categoriaId,
                                  String descripcion, String direccionOsm, String barrioOsm, String foto) {
}
