package com.viactg.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.viactg.exception.CredencialGoogleException;
import com.viactg.exception.ServicioNoDisponibleException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerificadorGoogleTests {
    private static final String CLIENT_ID = "123-abc.apps.googleusercontent.com";
    private static final KeyPair CLAVES_GOOGLE = generarClaves();
    private static final KeyPair OTRAS_CLAVES = generarClaves();

    private final VerificadorGoogle verificador = new VerificadorGoogle(CLIENT_ID,
            NimbusJwtDecoder.withPublicKey((RSAPublicKey) CLAVES_GOOGLE.getPublic()).build());

    private static KeyPair generarClaves() {
        try {
            KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
            generador.initialize(2048);
            return generador.generateKeyPair();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** Un ID token como los de Google, firmado con {@code claves}; {@code cambio} modifica los claims. */
    private static String token(KeyPair claves, Consumer<JWTClaimsSet.Builder> cambio) throws JOSEException {
        Instant ahora = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().issuer("https://accounts.google.com").audience(CLIENT_ID)
                .subject("109876543210").claim("email", "Ana@Gmail.com").claim("email_verified", true).claim("name", "Ana Pérez")
                .issueTime(Date.from(ahora)).expirationTime(Date.from(ahora.plusSeconds(3600)));
        cambio.accept(claims);
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        jwt.sign(new RSASSASigner(claves.getPrivate()));
        return jwt.serialize();
    }

    private static String token(Consumer<JWTClaimsSet.Builder> cambio) throws JOSEException {
        return token(CLAVES_GOOGLE, cambio);
    }

    @Test
    void unTokenValidoDevuelveLaIdentidad() throws JOSEException {
        IdentidadGoogle identidad = verificador.verificar(token(claims -> { }));

        assertThat(identidad).isEqualTo(new IdentidadGoogle("109876543210", "Ana@Gmail.com", true, "Ana Pérez"));
    }

    @Test
    void aceptaElEmisorSinHttps() throws JOSEException {
        assertThat(verificador.verificar(token(claims -> claims.issuer("accounts.google.com"))).sub()).isEqualTo("109876543210");
    }

    @Test
    void elCorreoSinVerificarSeInforma() throws JOSEException {
        assertThat(verificador.verificar(token(claims -> claims.claim("email_verified", false))).emailVerificado()).isFalse();
        assertThat(verificador.verificar(token(claims -> claims.claim("email_verified", null))).emailVerificado()).isFalse();
    }

    @Test
    void rechazaUnTokenEmitidoParaOtraAplicacion() {
        assertThatThrownBy(() -> verificador.verificar(token(claims -> claims.audience("otra-app.apps.googleusercontent.com"))))
                .isInstanceOf(CredencialGoogleException.class);
    }

    @Test
    void rechazaOtroEmisor() {
        assertThatThrownBy(() -> verificador.verificar(token(claims -> claims.issuer("https://evil.example"))))
                .isInstanceOf(CredencialGoogleException.class);
    }

    @Test
    void rechazaUnTokenVencido() {
        Instant hace2Horas = Instant.now().minusSeconds(7200);
        assertThatThrownBy(() -> verificador.verificar(token(claims -> claims.issueTime(Date.from(hace2Horas))
                .expirationTime(Date.from(hace2Horas.plusSeconds(3600))))))
                .isInstanceOf(CredencialGoogleException.class);
    }

    @Test
    void rechazaUnaFirmaQueNoEsDeGoogle() {
        assertThatThrownBy(() -> verificador.verificar(token(OTRAS_CLAVES, claims -> { })))
                .isInstanceOf(CredencialGoogleException.class);
    }

    @Test
    void rechazaTextoQueNoEsUnJwt() {
        assertThatThrownBy(() -> verificador.verificar("no-es-un-jwt")).isInstanceOf(CredencialGoogleException.class);
    }

    @Test
    void rechazaUnTokenSinCorreo() {
        assertThatThrownBy(() -> verificador.verificar(token(claims -> claims.claim("email", null))))
                .isInstanceOf(CredencialGoogleException.class);
    }

    @Test
    void sinClientIdNoEstaHabilitadoYResponde503() {
        VerificadorGoogle sinConfigurar = new VerificadorGoogle("  ");

        assertThat(sinConfigurar.habilitado()).isFalse();
        assertThat(sinConfigurar.clientId()).isNull();
        assertThatThrownBy(() -> sinConfigurar.verificar("x")).isInstanceOf(ServicioNoDisponibleException.class)
                .hasMessage("El inicio de sesión con Google no está configurado");
    }

    @Test
    void conClientIdLoExponeParaElBoton() {
        assertThat(new VerificadorGoogle(" " + CLIENT_ID + " ").clientId()).isEqualTo(CLIENT_ID);
    }
}
