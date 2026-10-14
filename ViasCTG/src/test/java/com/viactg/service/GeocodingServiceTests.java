package com.viactg.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeocodingServiceTests {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void extraeViaYBarrioDeLaRespuestaDeNominatim() throws Exception {
        GeocodingService.DireccionOsm direccion = GeocodingService.extraer(objectMapper.readTree("""
                {"display_name": "Calle 26, Manga, Cartagena, Bolívar, Colombia",
                 "address": {"road": "Calle 26", "suburb": "Manga", "city": "Cartagena"}}"""));

        assertThat(direccion.direccion()).isEqualTo("Calle 26, Manga");
        assertThat(direccion.barrio()).isEqualTo("Manga");
    }

    @Test
    void sinViaNiBarrioUsaElNombreCompleto() throws Exception {
        GeocodingService.DireccionOsm direccion = GeocodingService.extraer(objectMapper.readTree("""
                {"display_name": "Bahía de Cartagena, Bolívar, Colombia", "address": {"bay": "Bahía de Cartagena"}}"""));

        assertThat(direccion.direccion()).isEqualTo("Bahía de Cartagena, Bolívar, Colombia");
        assertThat(direccion.barrio()).isNull();
    }

    @Test
    void reservaTurnosDeUnSegundoYDescartaLosQueEsperarianDemasiado() {
        AtomicLong ahora = new AtomicLong(10_000);
        GeocodingService service = new GeocodingService(mock(HttpClient.class), objectMapper, "https://nominatim.test", "ViaCTG-test", ahora::get);

        assertThat(service.reservarTurno(2_000)).isZero();
        assertThat(service.reservarTurno(2_000)).isEqualTo(1_000);
        assertThat(service.reservarTurno(2_000)).isEqualTo(2_000);
        // El cuarto tendría que esperar 3 s: una consulta interactiva no se queda bloqueada en la cola.
        assertThat(service.reservarTurno(2_000)).isEqualTo(-1);
        ahora.addAndGet(3_000);
        assertThat(service.reservarTurno(2_000)).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void guardaEnCacheLasDireccionesDePuntosCercanos() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> respuesta = mock(HttpResponse.class);
        when(respuesta.statusCode()).thenReturn(200);
        when(respuesta.body()).thenReturn("{\"address\": {\"road\": \"Avenida Pedro de Heredia\", \"neighbourhood\": \"Pie de la Popa\"}}");
        doReturn(respuesta).when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        GeocodingService service = new GeocodingService(httpClient, objectMapper, "https://nominatim.test", "ViaCTG-test",
                new AtomicLong(0)::get);

        GeocodingService.DireccionOsm primera = service.resolver(10.414210, -75.535120);
        // A menos de 11 m: misma celda de la caché, no se vuelve a consultar Nominatim.
        GeocodingService.DireccionOsm segunda = service.resolver(10.414230, -75.535140);

        assertThat(primera.direccion()).isEqualTo("Avenida Pedro de Heredia, Pie de la Popa");
        assertThat(segunda).isEqualTo(primera);
        verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void sinUrlConfiguradaNoConsultaNada() {
        HttpClient httpClient = mock(HttpClient.class);
        GeocodingService service = new GeocodingService(httpClient, objectMapper, "", "ViaCTG-test", System::currentTimeMillis);

        assertThat(service.resolver(10.4, -75.5)).isNull();
    }
}
