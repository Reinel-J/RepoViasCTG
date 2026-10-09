package com.viactg.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class CorreoService {
    private static final Logger log = LoggerFactory.getLogger(CorreoService.class);

    private final JavaMailSender mailSender;
    private final String remitente;
    // El envío es asíncrono para que el tiempo de respuesta no revele si el correo existe.
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread hilo = new Thread(runnable, "envio-correo");
        hilo.setDaemon(true);
        return hilo;
    });

    public CorreoService(ObjectProvider<JavaMailSender> mailSender, @Value("${spring.mail.host:}") String host,
                         @Value("${app.mail.from:}") String remitente) {
        // Con SPRING_MAIL_HOST vacío Spring puede crear el cliente igualmente; se trata como "sin SMTP".
        this.mailSender = host.isBlank() ? null : mailSender.getIfAvailable();
        this.remitente = remitente.isBlank() ? "no-reply@viasctg.local" : remitente;
    }

    public void enviar(String destinatario, String asunto, String cuerpo) {
        if (mailSender == null) {
            log.warn("SMTP no configurado (SPRING_MAIL_HOST). Correo para {} no enviado:\n{}", destinatario, cuerpo);
            return;
        }
        executor.execute(() -> {
            try {
                SimpleMailMessage mensaje = new SimpleMailMessage();
                mensaje.setFrom(remitente);
                mensaje.setTo(destinatario);
                mensaje.setSubject(asunto);
                mensaje.setText(cuerpo);
                mailSender.send(mensaje);
            } catch (RuntimeException exception) {
                log.error("No fue posible enviar el correo a {}", destinatario, exception);
            }
        });
    }
}
