package com.viactg.service;

import com.viactg.dto.DashboardResponse;
import com.viactg.exception.ServicioNoDisponibleException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
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

    private final String siteUrl;
    private final String secretKey;
    private final String dashboardId;
    private final long tokenMinutos;

    public MetabaseService(@Value("${app.metabase.site-url:}") String siteUrl,
                           @Value("${app.metabase.secret-key:}") String secretKey,
                           @Value("${app.metabase.dashboard-id:}") String dashboardId,
                           @Value("${app.metabase.token-minutos:10}") long tokenMinutos) {
        this.siteUrl = siteUrl.strip().replaceAll("/+$", "");
        this.secretKey = secretKey.strip();
        this.dashboardId = dashboardId.strip();
        this.tokenMinutos = Math.max(1, tokenMinutos);
    }

    public DashboardResponse urlDashboard() {
        if (siteUrl.isEmpty() || secretKey.isEmpty() || dashboardId.isEmpty()) {
            throw new ServicioNoDisponibleException(SIN_CONFIGURAR);
        }
        long dashboard = numeroDashboard();
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
        return new DashboardResponse(siteUrl + "/embed/dashboard/" + token + "#bordered=false&titled=true", expiraEn);
    }

    private long numeroDashboard() {
        try {
            return Long.parseLong(dashboardId);
        } catch (NumberFormatException noEsNumero) {
            log.warn("METABASE_DASHBOARD_ID debe ser el número del dashboard en Metabase (el de /dashboard/<id>)");
            throw new ServicioNoDisponibleException(SIN_CONFIGURAR);
        }
    }
}
