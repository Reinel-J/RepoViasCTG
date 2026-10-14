package com.viactg.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.time.Duration;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final String uploadsResourceLocation;

    public WebConfig(@Value("${app.uploads.dir:uploads}") String uploadsDirectory) {
        // toUri() ya termina en "/" si la carpeta existe al arrancar; se evita duplicarla.
        String ubicacion = Path.of(uploadsDirectory).toAbsolutePath().toUri().toString();
        this.uploadsResourceLocation = ubicacion.endsWith("/") ? ubicacion : ubicacion + "/";
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Cada foto tiene un nombre UUID que nunca se reutiliza: el navegador puede guardarla en caché un año.
        registry.addResourceHandler("/uploads/**").addResourceLocations(uploadsResourceLocation)
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic());
    }
}
