package com.viactg.controller;

import com.viactg.exception.TooManyRequestsException;
import com.viactg.service.GeocodingService;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GeocodingControllerTests extends ControllerTestBase {

    @Test
    void devuelveLaDireccionElBarrioYSiElPuntoEstaEnElArea() throws Exception {
        when(geocodingService.resolver(10.42, -75.54)).thenReturn(new GeocodingService.DireccionOsm("Calle 30, Manga", "Manga"));
        when(areaServicio.contiene(10.42, -75.54)).thenReturn(true);

        mvc.perform(get("/api/geocoding/inverso").param("lat", "10.42").param("lng", "-75.54")
                        .with(peticion -> {
                            peticion.setRemoteAddr("198.51.100.4");
                            return peticion;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direccionOsm").value("Calle 30, Manga"))
                .andExpect(jsonPath("$.barrioOsm").value("Manga"))
                .andExpect(jsonPath("$.dentroDelArea").value(true));

        verify(limitador).consumir("geocoding-ip:198.51.100.4", 30, Duration.ofMinutes(1));
    }

    @Test
    void siNominatimNoRespondeLaDireccionSaleNullPeroSeIndicaElArea() throws Exception {
        when(geocodingService.resolver(4.6, -74.08)).thenReturn(null);
        when(areaServicio.contiene(4.6, -74.08)).thenReturn(false);

        mvc.perform(get("/api/geocoding/inverso").param("lat", "4.6").param("lng", "-74.08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direccionOsm").isEmpty())
                .andExpect(jsonPath("$.barrioOsm").isEmpty())
                .andExpect(jsonPath("$.dentroDelArea").value(false));
    }

    @Test
    void coordenadasFueraDeRangoDevuelven400() throws Exception {
        mvc.perform(get("/api/geocoding/inverso").param("lat", "10.42").param("lng", "-181"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors['inverso.lng']").value("La longitud debe estar entre -180 y 180"));

        verifyNoInteractions(geocodingService, limitador);
    }

    @Test
    void superarElLimitePorIpDevuelve429SinConsultarNominatim() throws Exception {
        doThrow(new TooManyRequestsException("Demasiadas consultas de dirección. Espera un minuto"))
                .when(limitador).consumir(anyString(), anyInt(), any());

        mvc.perform(get("/api/geocoding/inverso").param("lat", "10.42").param("lng", "-75.54"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Demasiadas consultas de dirección. Espera un minuto"));

        verifyNoInteractions(geocodingService);
    }
}
