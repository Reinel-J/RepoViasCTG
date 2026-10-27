package com.viactg.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viactg.exception.ErrorResponse;
import com.viactg.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) { this.jwtAuthenticationFilter = jwtAuthenticationFilter; }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        return http.csrf(csrf -> csrf.disable()).cors(Customizer.withDefaults())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, exception) -> escribirError(objectMapper, response,
                                HttpStatus.UNAUTHORIZED, "Debes iniciar sesión para realizar esta acción, o tu sesión ya venció"))
                        .accessDeniedHandler((request, response, exception) -> escribirError(objectMapper, response,
                                HttpStatus.FORBIDDEN, "No tienes permiso para realizar esta acción")))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/swagger-ui/**", "/v3/api-docs/**", "/uploads/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        // Los roles se exigen también aquí, no solo con @PreAuthorize: este filtro corre antes de leer
                        // el cuerpo, así que sin permiso la respuesta es 403 y no los errores de validación (400) de
                        // un formulario que no se le deja usar. Mantener en sincronía con los @PreAuthorize.
                        .requestMatchers("/api/admin/reportes/seguimiento/recordatorios").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "MODERADOR")
                        .requestMatchers(HttpMethod.GET, "/api/usuarios").hasRole("ADMIN")
                        .requestMatchers("/api/usuarios/*/rol", "/api/usuarios/*/activo").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/barrios/**", "/api/categorias/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/barrios/**", "/api/categorias/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/barrios/**", "/api/categorias/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/barrios/**", "/api/categorias/**").hasRole("ADMIN")
                        // Antes de la regla pública de GET /api/reportes/**: el primer patrón que coincide decide.
                        .requestMatchers(HttpMethod.GET, "/api/reportes/mios").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/reportes/**", "/api/barrios/**", "/api/categorias/**", "/api/geocoding/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class).build();
    }

    // URL que el firewall de Spring Security rechaza (//, %2e%2e, ;, ...). Sin esto se respondía 401 "inicia sesión".
    @Bean
    public WebSecurityCustomizer urlsRechazadas(ObjectMapper objectMapper) {
        return web -> web.requestRejectedHandler((request, response, exception) -> escribirError(objectMapper, response,
                HttpStatus.BAD_REQUEST, "La dirección solicitada no es válida"));
    }

    // Los errores de seguridad ocurren antes del @ControllerAdvice: se escribe a mano el mismo ErrorResponse.
    private static void escribirError(ObjectMapper objectMapper, HttpServletResponse response, HttpStatus status, String mensaje)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), mensaje, Map.of()));
    }

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
