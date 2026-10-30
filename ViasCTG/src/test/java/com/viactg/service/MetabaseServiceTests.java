package com.viactg.service;

import com.viactg.dto.DashboardResponse;
import com.viactg.exception.ServicioNoDisponibleException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetabaseServiceTests {
    // Mismo formato que la clave que genera Metabase: 64 caracteres hexadecimales.
    private static final String CLAVE = "3f0c8a1b9e7d6c5b4a39281706f5e4d3c2b1a09f8e7d6c5b4a3928170f6e5d4c";
    private static final String SITIO = "http://localhost:3000";

    private final MetabaseService service = new MetabaseService(SITIO, CLAVE, "7", 10);

    private static Jws<Claims> verificar(String token) {
        return Jwts.parser().verifyWith(Keys.hmacShaKeyFor(CLAVE.getBytes(StandardCharsets.UTF_8))).build().parseSignedClaims(token);
    }

    private static String token(DashboardResponse respuesta) {
        String sinFragmento = respuesta.url().substring(0, respuesta.url().indexOf('#'));
        return sinFragmento.substring(sinFragmento.lastIndexOf('/') + 1);
    }

    @Test
    void elTokenSeVerificaConLaMismaClaveYConHs256() {
        Jws<Claims> jws = verificar(token(service.urlDashboard()));

        // Con una clave de 64 bytes JJWT elegiría HS512 si no se forzara; Metabase espera HS256.
        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("HS256");
    }

    @Test
    void elTokenIndicaElDashboardYNoLlevaParametros() {
        Claims claims = verificar(token(service.urlDashboard())).getPayload();

        assertThat(claims.get("resource", Map.class)).containsEntry("dashboard", 7);
        assertThat(claims.get("params", Map.class)).isEmpty();
    }

    @Test
    void elTokenVenceEnLosMinutosConfigurados() {
        Instant antes = Instant.now();
        DashboardResponse respuesta = service.urlDashboard();
        Claims claims = verificar(token(respuesta)).getPayload();

        assertThat(claims.getExpiration().toInstant()).isEqualTo(respuesta.expiraEn());
        assertThat(Duration.between(antes, respuesta.expiraEn())).isBetween(Duration.ofSeconds(595), Duration.ofSeconds(600));
    }

    @Test
    void laUrlApuntaAlEmbebidoDelDashboardEnMetabase() {
        DashboardResponse respuesta = service.urlDashboard();

        assertThat(respuesta.url()).matches("http://localhost:3000/embed/dashboard/[\\w-]+\\.[\\w-]+\\.[\\w-]+#bordered=false&titled=true");
    }

    @Test
    void laBarraFinalDeLaDireccionNoSeDuplica() {
        DashboardResponse respuesta = new MetabaseService(SITIO + "/", CLAVE, "7", 10).urlDashboard();

        assertThat(respuesta.url()).startsWith("http://localhost:3000/embed/dashboard/");
    }

    @Test
    void sinConfiguracionResponde503() {
        for (MetabaseService incompleto : new MetabaseService[]{
                new MetabaseService("", CLAVE, "7", 10),
                new MetabaseService(SITIO, "", "7", 10),
                new MetabaseService(SITIO, CLAVE, " ", 10)}) {
            assertThatThrownBy(incompleto::urlDashboard)
                    .isInstanceOf(ServicioNoDisponibleException.class)
                    .hasMessage("El dashboard no está configurado");
        }
    }

    @Test
    void unIdQueNoEsNumeroOUnaClaveCortaCuentanComoSinConfigurar() {
        assertThatThrownBy(new MetabaseService(SITIO, CLAVE, "ventas", 10)::urlDashboard)
                .isInstanceOf(ServicioNoDisponibleException.class)
                .hasMessage("El dashboard no está configurado");
        assertThatThrownBy(new MetabaseService(SITIO, "corta", "7", 10)::urlDashboard)
                .isInstanceOf(ServicioNoDisponibleException.class)
                .hasMessage("El dashboard no está configurado");
    }
}
