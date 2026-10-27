package com.viactg.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Tomcat embebido real con el customizer de WebConfig: las URL que rechaza el conector nunca llegan a Spring. */
class ErroresTomcatJsonTests {
    private WebServer servidor;

    @BeforeEach
    void iniciarTomcat() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory(0);
        new WebConfig("uploads").erroresTomcatEnJson().customize(factory);
        servidor = factory.getWebServer();
        servidor.start();
    }

    @AfterEach
    void detenerTomcat() {
        servidor.stop();
    }

    // Peticiones crudas: un cliente HTTP normal se negaría a enviar estas URL.
    private String peticionCruda(String ruta) throws IOException {
        try (Socket socket = new Socket("localhost", servidor.getPort())) {
            OutputStream salida = socket.getOutputStream();
            salida.write(("GET " + ruta + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
            salida.flush();
            InputStream entrada = socket.getInputStream();
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/uploads/%", "/uploads/..%2f..%2fetc", "/uploads/a%00b", "/api/reportes/%zz"})
    void unaUrlMalFormadaRecibeElMismoJsonDeErrorQueElRestoDeLaApi(String ruta) throws IOException {
        String respuesta = peticionCruda(ruta);

        assertThat(respuesta).startsWith("HTTP/1.1 400");
        assertThat(respuesta).containsIgnoringCase("Content-Type: application/json");
        assertThat(respuesta).contains("\"status\":400", "\"error\":\"Bad Request\"",
                "\"message\":\"La dirección o los encabezados de la solicitud no son válidos\"", "\"validationErrors\":{}");
        assertThat(respuesta).doesNotContain("<html", "Tomcat", "Invalid URI");
    }
}
