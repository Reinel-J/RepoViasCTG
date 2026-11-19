package com.viactg.service;

import com.viactg.dto.DashboardResponse;
import com.viactg.exception.ServicioNoDisponibleException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

/**
 * URL firmada del dashboard de Metabase (embebido estático): un JWT con el dashboard y su vencimiento, firmado con la
 * clave de embebido de Metabase. La clave nunca sale del backend ni se escribe en los logs.
 */
@Service
public class MetabaseService {
    private static final Logger log = LoggerFactory.getLogger(MetabaseService.class);
    static final String SIN_CONFIGURAR = "El dashboard no está configurado";
    // HS256 exige una clave de al menos 256 bits; la que genera Metabase tiene 64 caracteres.
    private static final int BYTES_MINIMOS_CLAVE = 32;

    // Archivos que deja el servicio metabase-setup de docker compose (configurar-metabase.py --auto).
    static final String ARCHIVO_CLAVE = "secret-key";
    static final String ARCHIVO_DASHBOARD = "dashboard-id";

    private final String siteUrl;
    private final String secretKeyFija;
    private final String dashboardIdFijo;
    private final Path configDir;
    private final long tokenMinutos;

    /**
     * La clave y el número del dashboard se toman de las variables (configuración a mano) o, si están vacías, de los
     * archivos de {@code configDir}. Los archivos se leen en cada petición: el dashboard queda disponible en cuanto
     * metabase-setup termina, sin reiniciar el backend.
     */
    @Autowired
    public MetabaseService(@Value("${app.metabase.site-url:}") String siteUrl,
                           @Value("${app.metabase.secret-key:}") String secretKey,
                           @Value("${app.metabase.dashboard-id:}") String dashboardId,
                           @Value("${app.metabase.config-dir:}") String configDir,
                           @Value("${app.metabase.token-minutos:10}") long tokenMinutos) {
        this.siteUrl = siteUrl.strip().replaceAll("/+$", "");
        this.secretKeyFija = secretKey.strip();
        this.dashboardIdFijo = dashboardId.strip();
        this.configDir = configDir.isBlank() ? null : Path.of(configDir.strip());
        this.tokenMinutos = Math.max(1, tokenMinutos);
    }

    MetabaseService(String siteUrl, String secretKey, String dashboardId, long tokenMinutos) {
        this(siteUrl, secretKey, dashboardId, "", tokenMinutos);
    }

    public DashboardResponse urlDashboard() {
        String secretKey = secretKeyFija.isEmpty() ? leerArchivo(ARCHIVO_CLAVE) : secretKeyFija;
        String dashboardId = dashboardIdFijo.isEmpty() ? leerArchivo(ARCHIVO_DASHBOARD) : dashboardIdFijo;
        if (siteUrl.isEmpty() || secretKey.isEmpty() || dashboardId.isEmpty()) {
            throw new ServicioNoDisponibleException(SIN_CONFIGURAR);
        }
        long dashboard = numeroDashboard(dashboardId);
        byte[] clave = secretKey.getBytes(StandardCharsets.UTF_8);
        if (clave.length < BYTES_MINIMOS_CLAVE) {
            log.warn("METABASE_SECRET_KEY es demasiado corta: cópiala completa de Admin > Embedding de Metabase");
            throw new ServicioNoDisponibleException(SIN_CONFIGURAR);
        }
        SecretKey llave = Keys.hmacShaKeyFor(clave);
        // Segundos exactos: el claim exp de un JWT no lleva milisegundos.
        Instant expiraEn = Instant.now().plus(Duration.ofMinutes(tokenMinutos)).truncatedTo(ChronoUnit.SECONDS);
        // HS256 forzado: con una clave de 64 bytes JJWT elegiría HS512, y Metabase firma y verifica con HS256.
        String token = Jwts.builder()
                .claim("resource", Map.of("dashboard", dashboard))
                .claim("params", Map.of())
                .expiration(Date.from(expiraEn))
                .signWith(llave, Jwts.SIG.HS256)
                .compact();
        // refresh=60: el embebido vuelve a consultar sus tarjetas cada minuto (el auto-refresco que se elige en la
        // interfaz de Metabase no aplica a los dashboards embebidos).
        return new DashboardResponse(siteUrl + "/embed/dashboard/" + token + "#bordered=false&titled=true&refresh=60", expiraEn);
    }

    private String leerArchivo(String nombre) {
        if (configDir == null) {
            return "";
        }
        try {
            return Files.readString(configDir.resolve(nombre), StandardCharsets.UTF_8).strip();
        } catch (NoSuchFileException todaviaNo) {
            return "";  // metabase-setup aún no termina (o no se usa docker compose)
        } catch (IOException error) {
            log.warn("No se pudo leer {} en {}: {}", nombre, configDir, error.getMessage());
            return "";
        }
    }

    private static long numeroDashboard(String dashboardId) {
        try {
            return Long.parseLong(dashboardId);
        } catch (NumberFormatException noEsNumero) {
            log.warn("METABASE_DASHBOARD_ID debe ser el número del dashboard en Metabase (el de /dashboard/<id>)");
            throw new ServicioNoDisponibleException(SIN_CONFIGURAR);
        }
    }
}
