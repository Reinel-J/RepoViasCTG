package com.viactg.service;

import com.viactg.exception.ForbiddenOperationException;
import com.viactg.exception.TooManyRequestsException;
import com.viactg.model.Usuario;
import com.viactg.repository.TokenVerificacionRepository;
import com.viactg.security.IdentidadGoogle;
import com.viactg.security.LimitadorIntentos;
import com.viactg.security.VerificadorGoogle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccesoGoogleServiceTests {
    private final VerificadorGoogle verificador = mock(VerificadorGoogle.class);
    private final UsuarioService usuarioService = mock(UsuarioService.class);
    private final TokenVerificacionRepository tokenVerificacionRepository = mock(TokenVerificacionRepository.class);
    private final AccesoGoogleService service = new AccesoGoogleService(verificador, usuarioService, tokenVerificacionRepository,
            new LimitadorIntentos());

    @Test
    void entraConElCorreoNormalizadoYBorraElEnlaceDeConfirmacionPendiente() {
        when(verificador.verificar("jwt")).thenReturn(new IdentidadGoogle("sub-1", " Ana@Gmail.COM ", true, "Ana"));
        Usuario ana = Usuario.builder().id("u-1").build();
        when(usuarioService.accederConGoogle("sub-1", "ana@gmail.com", "Ana")).thenReturn(ana);

        assertThat(service.acceder("jwt", "1.1.1.1")).isSameAs(ana);
        verify(tokenVerificacionRepository).deleteByUsuarioId("u-1");
    }

    @Test
    void rechazaUnaCuentaDeGoogleConElCorreoSinVerificar() {
        when(verificador.verificar("jwt")).thenReturn(new IdentidadGoogle("sub-1", "ana@empresa.com", false, "Ana"));

        assertThatThrownBy(() -> service.acceder("jwt", "1.1.1.1")).isInstanceOf(ForbiddenOperationException.class);
        verify(usuarioService, never()).accederConGoogle(anyString(), anyString(), anyString());
    }

    @Test
    void limitaLosIntentosPorIp() {
        when(verificador.verificar("jwt")).thenReturn(new IdentidadGoogle("sub-1", "ana@gmail.com", true, "Ana"));
        when(usuarioService.accederConGoogle(anyString(), anyString(), anyString())).thenReturn(Usuario.builder().id("u-1").build());
        for (int i = 0; i < 20; i++) {
            service.acceder("jwt", "1.1.1.1");
        }

        assertThatThrownBy(() -> service.acceder("jwt", "1.1.1.1")).isInstanceOf(TooManyRequestsException.class);
        assertThat(service.acceder("jwt", "2.2.2.2").getId()).isEqualTo("u-1");
    }

    @Test
    void elClientIdSaleDelVerificador() {
        when(verificador.clientId()).thenReturn("123.apps.googleusercontent.com");

        assertThat(service.clientId()).isEqualTo("123.apps.googleusercontent.com");
    }
}
