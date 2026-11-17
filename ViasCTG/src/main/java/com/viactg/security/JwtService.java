package com.viactg.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtService(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationMs = expirationMs;
    }

    public String generarToken(UsuarioPrincipal principal) {
        Date ahora = new Date();
        return Jwts.builder().subject(principal.getUsername()).claim("uid", principal.getId())
                .claim("rol", principal.getUsuario().getRol().name())
                .claim("ver", principal.getUsuario().getVersionToken()).issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + expirationMs)).signWith(secretKey).compact();
    }

    public String extraerEmail(String token) { return claims(token).getSubject(); }

    public boolean esValido(String token, UserDetails userDetails) {
        Claims claims = claims(token);
        Integer version = claims.get("ver", Integer.class);
        int versionActual = userDetails instanceof UsuarioPrincipal principal ? principal.getUsuario().getVersionToken() : 0;
        return claims.getSubject().equalsIgnoreCase(userDetails.getUsername()) && claims.getExpiration().after(new Date())
                && (version == null ? 0 : version) == versionActual;
    }

    private Claims claims(String token) {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
    }
}
