package com.viactg.controller;

import com.viactg.dto.GeocodingInversoResponse;
import com.viactg.security.LimitadorIntentos;
import com.viactg.service.AreaServicio;
import com.viactg.service.GeocodingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@Validated
@RequestMapping("/api/geocoding")
public class GeocodingController {
    // Endpoint público que consume el cupo global de Nominatim: se limita por IP para que nadie lo acapare.
    private static final int MAX_CONSULTAS_POR_MINUTO = 30;

    private final GeocodingService geocodingService;
    private final AreaServicio areaServicio;
    private final LimitadorIntentos limitador;

    public GeocodingController(GeocodingService geocodingService, AreaServicio areaServicio, LimitadorIntentos limitador) {
        this.geocodingService = geocodingService;
        this.areaServicio = areaServicio;
        this.limitador = limitador;
    }

    @GetMapping("/inverso")
    public GeocodingInversoResponse inverso(
            @RequestParam @DecimalMin(value = "-90.0", message = "La latitud debe estar entre -90 y 90")
            @DecimalMax(value = "90.0", message = "La latitud debe estar entre -90 y 90") double lat,
            @RequestParam @DecimalMin(value = "-180.0", message = "La longitud debe estar entre -180 y 180")
            @DecimalMax(value = "180.0", message = "La longitud debe estar entre -180 y 180") double lng,
            HttpServletRequest http) {
        limitador.consumir("geocoding-ip:" + http.getRemoteAddr(), MAX_CONSULTAS_POR_MINUTO, Duration.ofMinutes(1));
        GeocodingService.DireccionOsm direccion = geocodingService.resolver(lat, lng);
        return new GeocodingInversoResponse(direccion == null ? null : direccion.direccion(),
                direccion == null ? null : direccion.barrio(), areaServicio.contiene(lat, lng));
    }
}
