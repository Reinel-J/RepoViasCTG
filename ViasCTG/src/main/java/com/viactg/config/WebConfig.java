package com.viactg.config;

import org.apache.catalina.Host;
import org.apache.catalina.Valve;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.valves.ErrorReportValve;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
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

    /**
     * Cambia la válvula de errores de Tomcat (página HTML) por {@link ErroresTomcatJson}. Spring Boot agrega la suya
     * al Host en un customizer anterior; se quita y se registra la clase propia para que Tomcat no agregue otra.
     */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> erroresTomcatEnJson() {
        return factory -> factory.addContextCustomizers(context -> {
            Host host = (Host) context.getParent();
            for (Valve valve : host.getPipeline().getValves()) {
                if (valve instanceof ErrorReportValve) host.getPipeline().removeValve(valve);
            }
            if (host instanceof StandardHost standardHost) standardHost.setErrorReportValveClass(ErroresTomcatJson.class.getName());
            host.getPipeline().addValve(new ErroresTomcatJson());
        });
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Cada foto tiene un nombre UUID que nunca se reutiliza: el navegador puede guardarla en caché un año.
        registry.addResourceHandler("/uploads/**").addResourceLocations(uploadsResourceLocation)
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic());
    }
}
