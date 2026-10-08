package com.viactg.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

@Service
public class GeocodingService {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final long MINIMUM_REQUEST_INTERVAL_MS = 1_000;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private long lastRequestAt;

    @Autowired
    public GeocodingService(ObjectMapper objectMapper) {
        this(HttpClient.newBuilder().connectTimeout(TIMEOUT).build(), objectMapper);
    }

    GeocodingService(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public String resolverDireccion(double latitud, double longitud) {
        try {
            esperarTurno();
            String url = String.format(Locale.ROOT,
                    "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=%s&lon=%s&addressdetails=1",
                    URLEncoder.encode(Double.toString(latitud), StandardCharsets.UTF_8),
                    URLEncoder.encode(Double.toString(longitud), StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Accept", "application/json")
                    .header("User-Agent", "ViaCTG/1.0 (contacto@ejemplo.com)")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            JsonNode body = objectMapper.readTree(response.body());
            String road = body.path("address").path("road").asText(null);
            return road == null || road.isBlank() ? body.path("display_name").asText(null) : road;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception exception) {
            return null;
        }
    }

    private synchronized void esperarTurno() throws InterruptedException {
        long waitTime = MINIMUM_REQUEST_INTERVAL_MS - (System.currentTimeMillis() - lastRequestAt);
        if (waitTime > 0) {
            Thread.sleep(waitTime);
        }
        lastRequestAt = System.currentTimeMillis();
    }
}
