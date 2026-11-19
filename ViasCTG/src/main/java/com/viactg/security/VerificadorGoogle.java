package com.viactg.security;

import com.viactg.exception.CredencialGoogleException;
import com.viactg.exception.ServicioNoDisponibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Verifica el ID token que entrega «Iniciar sesión con Google» (Google Identity Services) en el navegador: firma RS256
 * con las claves públicas de Google (se descargan y se guardan en caché), emisor de Google, audiencia igual a nuestro
 * Client ID (un token emitido para otra aplicación no sirve aquí) y vigencia.
 */
@Component
public class VerificadorGoogle {
    private static final Logger log = LoggerFactory.getLogger(VerificadorGoogle.class);
    static final String CLAVES_GOOGLE = "https://www.googleapis.com/oauth2/v3/certs";
    // Google usa los dos valores en el «iss» de sus ID token.
    static final Set<String> EMISORES = Set.of("https://accounts.google.com", "accounts.google.com");
    private static final String TOKEN_INVALIDO = "La sesión de Google no es válida o ya venció. Intenta de nuevo.";

    private final String clientId;
    private final NimbusJwtDecoder decoder;

    @Autowired
    public VerificadorGoogle(@Value("${app.google.client-id:}") String clientId) {
        this(clientId, clientId.isBlank() ? null : NimbusJwtDecoder.withJwkSetUri(CLAVES_GOOGLE).jwsAlgorithm(SignatureAlgorithm.RS256).build());
    }

    /** Para las pruebas: un decodificador con una clave propia en lugar de las de Google. */
    VerificadorGoogle(String clientId, NimbusJwtDecoder decoder) {
        this.clientId = clientId.strip();
        this.decoder = decoder;
        if (decoder != null) {
            decoder.setJwtValidator(validador(this.clientId));
        }
    }

    public boolean habilitado() {
        return decoder != null;
    }

    /** El Client ID es público (va en el HTML de cualquier sitio con el botón): el frontend lo necesita para mostrarlo. */
    public String clientId() {
        return habilitado() ? clientId : null;
    }

    public IdentidadGoogle verificar(String credencial) {
        if (!habilitado()) {
            throw new ServicioNoDisponibleException("El inicio de sesión con Google no está configurado");
        }
        Jwt jwt;
        try {
            jwt = decoder.decode(credencial);
        } catch (BadJwtException invalido) {
            // Firma, emisor, audiencia o vigencia incorrectos, o un texto que no es un JWT.
            throw new CredencialGoogleException(TOKEN_INVALIDO);
        } catch (JwtException sinClaves) {
            // No se pudieron descargar las claves de Google (red, DNS): no es culpa de quien intenta entrar.
            log.warn("No se pudo verificar un ID token de Google: {}", sinClaves.getMessage());
            throw new ServicioNoDisponibleException("No fue posible comprobar la sesión con Google. Intenta más tarde.");
        }
        String email = jwt.getClaimAsString("email");
        if (jwt.getSubject() == null || email == null || email.isBlank()) {
            throw new CredencialGoogleException(TOKEN_INVALIDO);
        }
        return new IdentidadGoogle(jwt.getSubject(), email, Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")),
                jwt.getClaimAsString("name"));
    }

    static OAuth2TokenValidator<Jwt> validador(String clientId) {
        OAuth2TokenValidator<Jwt> emisor = jwt -> EMISORES.contains(jwt.getClaimAsString("iss"))
                ? OAuth2TokenValidatorResult.success() : fallo("El token no lo emitió Google");
        OAuth2TokenValidator<Jwt> audiencia = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(clientId)
                ? OAuth2TokenValidatorResult.success() : fallo("El token es para otra aplicación");
        return new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), emisor, audiencia);
    }

    private static OAuth2TokenValidatorResult fallo(String descripcion) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", descripcion, null));
    }
}
