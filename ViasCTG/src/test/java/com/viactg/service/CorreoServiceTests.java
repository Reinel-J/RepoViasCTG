package com.viactg.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CorreoServiceTests {
    private static final long ESPERA_MS = 2000; // el envío corre en otro hilo

    private final JavaMailSender sender = mock(JavaMailSender.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<JavaMailSender> proveedor = mock(ObjectProvider.class);

    private CorreoService servicio(String host, String remitente) {
        when(proveedor.getIfAvailable()).thenReturn(sender);
        return new CorreoService(proveedor, host, remitente);
    }

    @Test
    void sinHostConfiguradoNoSeEnvianCorreosNiSeConsultaElProveedor() {
        CorreoService service = servicio("", "");

        assertThatCode(() -> service.enviar("ana@correo.com", "Asunto", "Cuerpo")).doesNotThrowAnyException();

        verifyNoInteractions(sender);
        verify(proveedor, never()).getIfAvailable();
    }

    @Test
    void unHostSoloConEspaciosTambienSeTrataComoSinSmtp() {
        CorreoService service = servicio("   ", "");

        service.enviar("ana@correo.com", "Asunto", "Cuerpo");

        verifyNoInteractions(sender);
    }

    @Test
    void conHostConfiguradoEnviaElMensajeConDestinatarioAsuntoYCuerpo() {
        CorreoService service = servicio("smtp.correo.com", "viactg@correo.com");

        service.enviar("ana@correo.com", "Restablece tu contraseña", "Abre este enlace");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender, timeout(ESPERA_MS)).send(captor.capture());
        SimpleMailMessage mensaje = captor.getValue();
        assertThat(mensaje.getTo()).containsExactly("ana@correo.com");
        assertThat(mensaje.getSubject()).isEqualTo("Restablece tu contraseña");
        assertThat(mensaje.getText()).isEqualTo("Abre este enlace");
        assertThat(mensaje.getFrom()).isEqualTo("viactg@correo.com");
    }

    @Test
    void sinRemitenteConfiguradoUsaElPredeterminado() {
        CorreoService service = servicio("smtp.correo.com", "");

        service.enviar("ana@correo.com", "Asunto", "Cuerpo");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender, timeout(ESPERA_MS)).send(captor.capture());
        assertThat(captor.getValue().getFrom()).isEqualTo("no-reply@viasctg.local");
    }

    @Test
    void siFallaElEnvioLaExcepcionNoLlegaAQuienLlama() {
        doThrow(new MailSendException("SMTP caído")).when(sender).send(any(SimpleMailMessage.class));
        CorreoService service = servicio("smtp.correo.com", "");

        assertThatCode(() -> service.enviar("ana@correo.com", "Asunto", "Cuerpo")).doesNotThrowAnyException();

        verify(sender, timeout(ESPERA_MS)).send(any(SimpleMailMessage.class));
    }

    @Test
    void despuesDeUnFalloElServicioSigueEnviandoCorreos() {
        doThrow(new MailSendException("SMTP caído")).doNothing().when(sender).send(any(SimpleMailMessage.class));
        CorreoService service = servicio("smtp.correo.com", "");

        service.enviar("uno@correo.com", "Primero", "Falla");
        service.enviar("dos@correo.com", "Segundo", "Sale bien");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender, timeout(ESPERA_MS).times(2)).send(captor.capture());
    }
}