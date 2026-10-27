package com.viactg.config;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ErrorReportValve;
import org.apache.coyote.ActionCode;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reemplaza la página HTML de error de Tomcat por el mismo JSON que {@code ErrorResponse}. Solo actúa con errores
 * que no llegan a Spring, como las URL que Tomcat rechaza al leerlas ({@code %} inválido, {@code %2F}, bytes nulos).
 * Tomcat la instancia por su cuenta, así que el JSON se arma a mano (sin Jackson) y con textos fijos.
 */
public class ErroresTomcatJson extends ErrorReportValve {

    @Override
    protected void report(Request request, Response response, Throwable throwable) {
        int codigo = response.getStatus();
        // Igual que la válvula original: nada si no es error, si ya se escribió algo o si ya se informó.
        if (codigo < 400 || response.getContentWritten() > 0 || !response.setErrorReported()) {
            return;
        }
        AtomicBoolean escribible = new AtomicBoolean(false);
        response.getCoyoteResponse().action(ActionCode.IS_IO_ALLOWED, escribible);
        if (!escribible.get()) {
            return;
        }
        HttpStatus status = HttpStatus.resolve(codigo);
        String error = status == null ? "Error" : status.getReasonPhrase();
        String mensaje = codigo == HttpStatus.BAD_REQUEST.value() ? "La dirección o los encabezados de la solicitud no son válidos"
                : "No fue posible procesar la solicitud";
        try {
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            Writer writer = response.getReporter();
            if (writer != null) {
                writer.write("{\"timestamp\":\"" + Instant.now() + "\",\"status\":" + codigo + ",\"error\":\"" + error
                        + "\",\"message\":\"" + mensaje + "\",\"validationErrors\":{}}");
                response.finishResponse();
            }
        } catch (IOException | IllegalStateException ignorada) {
            // La conexión ya no admite escritura: no hay a quién responder.
        }
    }
}
