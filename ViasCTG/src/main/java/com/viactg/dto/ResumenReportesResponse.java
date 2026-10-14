package com.viactg.dto;

import com.viactg.model.EstadoReporte;

import java.util.Map;

/** Cifras públicas de la portada, calculadas con conteos en la base de datos en lugar de descargar todos los reportes. */
public record ResumenReportesResponse(long total, Map<EstadoReporte, Long> porEstado, long creadosUltimas24Horas,
                                      long barrios) {
}
