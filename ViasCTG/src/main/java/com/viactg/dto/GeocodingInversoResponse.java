package com.viactg.dto;

/**
 * Vista previa de la ubicación elegida en el mapa: dirección legible (vía y barrio), el barrio por separado
 * (ambos null si no se pudieron resolver) y si el punto está dentro del área de servicio.
 */
public record GeocodingInversoResponse(String direccionOsm, String barrioOsm, boolean dentroDelArea) {
}
