package com.viactg.dto;

import java.util.List;

/**
 * Categorías y barrios que aparecen en algún reporte, sin repetidos y en orden alfabético, para los filtros. La
 * categoría se filtra por su id ({@code ?categoria=<id>}); el barrio, por su nombre.
 */
public record OpcionesFiltroResponse(List<CategoriaReporteResponse> categorias, List<String> barrios) {
}
