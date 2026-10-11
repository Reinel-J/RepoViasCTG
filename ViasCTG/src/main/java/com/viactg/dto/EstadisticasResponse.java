package com.viactg.dto;

import com.viactg.model.EstadoReporte;

import java.util.List;
import java.util.Map;

/**
 * Panel de estadísticas. Los promedios de días son null cuando aún no hay datos para calcularlos;
 * {@code porcentajeResuelto} se calcula sobre los reportes no rechazados.
 */
public record EstadisticasResponse(long total, Map<EstadoReporte, Long> porEstado, Integer porcentajeResuelto,
                                   Double diasPrimeraRespuesta, Double diasResolucion, List<Mes> meses,
                                   List<Categoria> categorias, List<CeldaDensidad> densidad) {

    /** {@code mes} en formato yyyy-MM, hora de Colombia. */
    public record Mes(String mes, long creados, long resueltos) {
    }

    public record Categoria(String nombre, long total, long resueltos) {
    }

    /** Centro de una celda de ~110 m con los reportes activos que contiene. */
    public record CeldaDensidad(double latitud, double longitud, long total) {
    }
}
