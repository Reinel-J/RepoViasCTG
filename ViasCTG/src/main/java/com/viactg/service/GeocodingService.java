package com.viactg.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Geocodificación inversa con Nominatim (OpenStreetMap), respetando su política de uso: como máximo una
 * petición por segundo para todo el servidor, User-Agent propio y resultados en caché.
 *
 * <p>Nunca se duerme con un candado tomado: cada llamada reserva el siguiente turno libre y espera fuera del
 * candado. Una consulta interactiva (la vista previa del mapa, que es pública) solo espera unos segundos; si la
 * cola es más larga devuelve null en lugar de bloquear un hilo de Tomcat, así que una ráfaga de peticiones no
 * puede dejar al resto de la API sin hilos.
 */
@Service
public class GeocodingService {
    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    static final long INTERVALO_MINIMO_MS = 1_000;
    static final long ESPERA_MAXIMA_INTERACTIVA_MS = 2_000;
    static final long ESPERA_MAXIMA_SEGUNDO_PLANO_MS = 60_000;
    static final int MAXIMO_EN_CACHE = 5_000;
    private static final int LONGITUD_MAXIMA = 200;
    static final String USER_AGENT_POR_DEFECTO = "ViaCTG/1.0 (+https://github.com/Reinel-J/RepoViasCTG)";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String userAgent;
    private final LongSupplier relojMs;
    private final Object turnoLock = new Object();
    private long proximoTurnoMs;
    // LRU: al superar el máximo se descarta la entrada usada hace más tiempo.
    private final Map<String, DireccionOsm> cache = Collections.synchronizedMap(new LinkedHashMap<String, DireccionOsm>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, DireccionOsm> masAntigua) {
            return size() > MAXIMO_EN_CACHE;
        }
    });

    @Autowired
    public GeocodingService(ObjectMapper objectMapper,
                            @Value("${app.geocoding.url:https://nominatim.openstreetmap.org}") String baseUrl,
                            @Value("${app.geocoding.user-agent:ViaCTG/1.0 (+https://github.com/Reinel-J/RepoViasCTG)}") String userAgent) {
        this(HttpClient.newBuilder().connectTimeout(TIMEOUT).build(), objectMapper, baseUrl, userAgent, System::currentTimeMillis);
    }

    GeocodingService(HttpClient httpClient, ObjectMapper objectMapper, String baseUrl, String userAgent, LongSupplier relojMs) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        // Una variable de entorno definida pero vacía no debe dejar las peticiones sin identificación.
        this.userAgent = userAgent == null || userAgent.isBlank() ? USER_AGENT_POR_DEFECTO : userAgent.trim();
        this.relojMs = relojMs;
    }

    /** Para la vista previa del mapa: si Nominatim está ocupado más de unos segundos, devuelve null. */
    public DireccionOsm resolver(double latitud, double longitud) {
        return resolver(latitud, longitud, ESPERA_MAXIMA_INTERACTIVA_MS);
    }

    /** Para tareas en segundo plano (dirección de un reporte recién guardado): espera su turno hasta un minuto. */
    public DireccionOsm resolverEnSegundoPlano(double latitud, double longitud) {
        return resolver(latitud, longitud, ESPERA_MAXIMA_SEGUNDO_PLANO_MS);
    }

    private DireccionOsm resolver(double latitud, double longitud, long esperaMaximaMs) {
        if (baseUrl.isEmpty()) return null;
        String clave = claveCache(latitud, longitud);
        DireccionOsm enCache = cache.get(clave);
        if (enCache != null) return enCache;
        long espera = reservarTurno(esperaMaximaMs);
        if (espera < 0) {
            log.debug("Cola de Nominatim llena; se omite la dirección de {}", clave);
            return null;
        }
        try {
            if (espera > 0) Thread.sleep(espera);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        }
        DireccionOsm direccion = consultar(latitud, longitud);
        // Solo se guardan respuestas válidas de Nominatim; un error de red se reintenta en la próxima consulta.
        if (direccion != null) cache.put(clave, direccion);
        return direccion;
    }

    /** 4 decimales ≈ 11 m: puntos tan cercanos comparten vía y barrio. */
    static String claveCache(double latitud, double longitud) {
        return String.format(Locale.ROOT, "%.4f,%.4f", latitud, longitud);
    }

    /** Reserva el siguiente turno libre. Devuelve los milisegundos que hay que esperar, o -1 si superan el máximo. */
    long reservarTurno(long esperaMaximaMs) {
        synchronized (turnoLock) {
            long ahora = relojMs.getAsLong();
            long turno = Math.max(ahora, proximoTurnoMs);
            long espera = turno - ahora;
            if (espera > esperaMaximaMs) return -1;
            proximoTurnoMs = turno + INTERVALO_MINIMO_MS;
            return espera;
        }
    }

    private DireccionOsm consultar(double latitud, double longitud) {
        String url = String.format(Locale.ROOT,
                "%s/reverse?format=jsonv2&lat=%.6f&lon=%.6f&addressdetails=1&accept-language=es", baseUrl, latitud, longitud);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Nominatim respondió {} al resolver {}", response.statusCode(), claveCache(latitud, longitud));
                return null;
            }
            return extraer(objectMapper.readTree(response.body()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException | RuntimeException exception) {
            log.debug("No fue posible consultar Nominatim para {}", claveCache(latitud, longitud), exception);
            return null;
        }
    }

    /** Arma "vía, barrio" con los campos de la respuesta jsonv2; si no hay vía ni barrio, usa el nombre completo. */
    static DireccionOsm extraer(JsonNode cuerpo) {
        JsonNode direccion = cuerpo.path("address");
        String via = primerTexto(direccion, "road", "pedestrian", "footway");
        String barrio = primerTexto(direccion, "neighbourhood", "quarter", "suburb");
        String texto;
        if (via != null && barrio != null && !via.equalsIgnoreCase(barrio)) {
            texto = via + ", " + barrio;
        } else if (via != null) {
            texto = via;
        } else if (barrio != null) {
            texto = barrio;
        } else {
            texto = primerTexto(cuerpo, "display_name");
        }
        return new DireccionOsm(recortar(texto), recortar(barrio));
    }

    private static String primerTexto(JsonNode nodo, String... campos) {
        for (String campo : campos) {
            String valor = nodo.path(campo).asText("").trim();
            if (!valor.isEmpty()) return valor;
        }
        return null;
    }

    private static String recortar(String texto) {
        if (texto == null) return null;
        return texto.length() <= LONGITUD_MAXIMA ? texto : texto.substring(0, LONGITUD_MAXIMA - 1) + "…";
    }

    /** Dirección legible (vía y barrio) y el barrio por separado; cualquiera puede ser null. */
    public record DireccionOsm(String direccion, String barrio) {
        public boolean vacia() {
            return direccion == null && barrio == null;
        }
    }
}
