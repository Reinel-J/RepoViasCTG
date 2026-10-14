package com.viactg.service;

import com.viactg.exception.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Área geográfica donde ViaCTG acepta reportes (regla de negocio: "las coordenadas deben validarse antes de
 * guardar el reporte"). Es un rectángulo configurable; por defecto cubre el área urbana de Cartagena, sus
 * corregimientos cercanos, Barú y las Islas del Rosario.
 */
@Component
public class AreaServicio {
    private final boolean habilitada;
    private final String nombre;
    private final double latitudMinima;
    private final double latitudMaxima;
    private final double longitudMinima;
    private final double longitudMaxima;

    public AreaServicio(@Value("${app.zona.habilitada:true}") boolean habilitada,
                        @Value("${app.zona.nombre:Cartagena}") String nombre,
                        @Value("${app.zona.lat-min:10.15}") double latitudMinima,
                        @Value("${app.zona.lat-max:10.65}") double latitudMaxima,
                        @Value("${app.zona.lng-min:-75.80}") double longitudMinima,
                        @Value("${app.zona.lng-max:-75.30}") double longitudMaxima) {
        this.habilitada = habilitada;
        this.nombre = nombre;
        this.latitudMinima = latitudMinima;
        this.latitudMaxima = latitudMaxima;
        this.longitudMinima = longitudMinima;
        this.longitudMaxima = longitudMaxima;
    }

    /** Área por defecto (Cartagena); útil en pruebas. */
    public static AreaServicio cartagena() {
        return new AreaServicio(true, "Cartagena", 10.15, 10.65, -75.80, -75.30);
    }

    public boolean contiene(double latitud, double longitud) {
        return !habilitada || (latitud >= latitudMinima && latitud <= latitudMaxima
                && longitud >= longitudMinima && longitud <= longitudMaxima);
    }

    public void validar(double latitud, double longitud) {
        if (!contiene(latitud, longitud)) {
            throw new BusinessRuleException("La ubicación está fuera del área de servicio de ViaCTG (" + nombre
                    + "). Marca en el mapa un punto dentro de la ciudad.");
        }
    }
}
