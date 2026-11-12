package com.viactg.dto;

import java.util.List;

/** Categorías y barrios que aparecen en algún reporte, sin repetidos y en orden alfabético, para los filtros. */
public record OpcionesFiltroResponse(List<String> categorias, List<String> barrios) {
}
