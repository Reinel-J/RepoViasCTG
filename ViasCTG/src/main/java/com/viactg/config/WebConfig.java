package com.viactg.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final String uploadsResourceLocation;

    public WebConfig(@Value("${app.uploads.dir:/app/uploads}") String uploadsDirectory) {
        this.uploadsResourceLocation = Path.of(uploadsDirectory).toAbsolutePath().toUri() + "/";
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**").addResourceLocations(uploadsResourceLocation);
    }
}
