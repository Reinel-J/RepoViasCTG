package com.viactg.dto;

import com.viactg.model.EstadoReporte;
import com.viactg.model.PrioridadReporte;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Panel de estadísticas. Los promedios de días son null cuando aún no hay datos para calcularlos;
 * {@code porcentajeResuelto} se calcula sobre los reportes no rechazados. {@code prioridadActivos} cuenta los
 * reportes activos por prioridad y {@code pendienteMasAntiguo} es la fecha de creación del pendiente más antiguo
 * (null si no hay pendientes).
 */
public record EstadisticasResponse(long total, Map<EstadoReporte, Long> porEstado, Integer porcentajeResuelto,
                                   Double diasPrimeraRespuesta, Double diasResolucion, List<Mes> meses,
                                   List<Categoria> categorias, List<CeldaDensidad> densidad,
                                   Map<PrioridadReporte, Long> prioridadActivos, Instant pendienteMasAntiguo) {

    /** {@code mes} en formato yyyy-MM, hora de Colombia. */
    public record Mes(String mes, long creados, long resueltos) {
    }

    /** Agrupada por el id del catálogo; {@code nombre} es el actual. */
    public record Categoria(String id, String nombre, long total, long resueltos) {
    }

    /** Centro de una celda de ~110 m con los reportes activos que contiene. */
    public record CeldaDensidad(double latitud, double longitud, long total) {
    }
}
