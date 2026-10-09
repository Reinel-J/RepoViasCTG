package com.viactg.service;

import com.viactg.exception.BusinessRuleException;
import com.viactg.exception.TooManyRequestsException;
import com.viactg.model.Rol;
import com.viactg.model.TokenRecuperacion;
import com.viactg.model.Usuario;
import com.viactg.repository.TokenRecuperacionRepository;
import com.viactg.repository.UsuarioRepository;
import com.viactg.security.LimitadorIntentos;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTests {
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final UsuarioService usuarioService = mock(UsuarioService.class);
    private final TokenRecuperacionRepository tokenRepository = mock(TokenRecuperacionRepository.class);
    private final CorreoService correoService = mock(CorreoService.class);
    // Costo mínimo de BCrypt para que las pruebas sean rápidas.
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final AuthService service = new AuthService(usuarioRepository, usuarioService, tokenRepository, encoder,
            new LimitadorIntentos(), correoService, "http://localhost:5173/", 30);

    private Usuario usuario(String password) {
        return Usuario.builder().id("u-1").nombre("Ana").email("ana@test.com").passwordHash(encoder.encode(password))
                .rol(Rol.CIUDADANO).activo(true).versionToken(0).build();
    }

    @Test
    void bloqueaElCorreoTrasCincoFallosAunqueLuegoLaContrasenaSeaCorrecta() {
        when(usuarioRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(usuario("Correcta123")));
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.login("ana@test.com", "mala", "1.1.1.1")).isInstanceOf(BadCredentialsException.class);
        }
        assertThatThrownBy(() -> service.login("ANA@test.com", "Correcta123", "2.2.2.2")).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void unLoginCorrectoReiniciaLosFallos() {
        when(usuarioRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(usuario("Correcta123")));
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> service.login("ana@test.com", "mala", "1.1.1.1")).isInstanceOf(BadCredentialsException.class);
        }
        service.login("ana@test.com", "Correcta123", "1.1.1.1");
        assertThatThrownBy(() -> service.login("ana@test.com", "mala", "1.1.1.1")).isInstanceOf(BadCredentialsException.class);
        assertThat(service.login("ana@test.com", "Correcta123", "1.1.1.1").getId()).isEqualTo("u-1");
    }

    @Test
    void correoInexistenteDaElMismoErrorQueContrasenaIncorrecta() {
        when(usuarioRepository.findByEmail("nadie@test.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login("nadie@test.com", "x", "1.1.1.1"))
                .isInstanceOf(BadCredentialsException.class).hasMessage("Credenciales inválidas o usuario inactivo");
    }

    @Test
    void recuperarGuardaSoloElHashYEnviaElEnlace() {
        when(usuarioRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(usuario("Correcta123")));

        service.solicitarRecuperacion("ana@test.com", "1.1.1.1");

        ArgumentCaptor<TokenRecuperacion> guardado = ArgumentCaptor.forClass(TokenRecuperacion.class);
        verify(tokenRepository).save(guardado.capture());
        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(correoService).enviar(eq("ana@test.com"), anyString(), cuerpo.capture());
        String token = cuerpo.getValue().replaceAll("(?s).*restablecer\\?token=(\\S+).*", "$1");
        assertThat(cuerpo.getValue()).contains("http://localhost:5173/restablecer?token=");
        assertThat(guardado.getValue().getTokenHash()).isEqualTo(AuthService.sha256(token)).isNotEqualTo(token);
    }

    @Test
    void recuperarConCorreoDesconocidoNoHaceNadaNiFalla() {
        when(usuarioRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        service.solicitarRecuperacion("nadie@test.com", "1.1.1.1");
        verify(tokenRepository, never()).save(any());
        verify(correoService, never()).enviar(anyString(), anyString(), anyString());
    }

    @Test
    void restablecerCambiaLaContrasenaYRevocaSesiones() {
        Usuario ana = usuario("Vieja12345");
        when(tokenRepository.findByTokenHash(AuthService.sha256("tok"))).thenReturn(Optional.of(
                TokenRecuperacion.builder().usuarioId("u-1").tokenHash(AuthService.sha256("tok")).expira(Instant.now().plusSeconds(60)).build()));
        when(usuarioService.buscarPorId("u-1")).thenReturn(ana);
        when(usuarioRepository.save(ana)).thenReturn(ana);

        service.restablecer("tok", "Nueva12345");

        assertThat(encoder.matches("Nueva12345", ana.getPasswordHash())).isTrue();
        assertThat(ana.getVersionToken()).isEqualTo(1);
        verify(tokenRepository).deleteByUsuarioId("u-1");
    }

    @Test
    void restablecerConTokenVencidoFalla() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(
                TokenRecuperacion.builder().usuarioId("u-1").expira(Instant.now().minusSeconds(1)).build()));
        assertThatThrownBy(() -> service.restablecer("tok", "Nueva12345")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void cambiarPasswordExigeLaActualYSubeLaVersion() {
        Usuario ana = usuario("Vieja12345");
        when(usuarioService.buscarPorId("u-1")).thenReturn(ana);
        when(usuarioRepository.save(ana)).thenReturn(ana);

        assertThatThrownBy(() -> service.cambiarPassword("u-1", "incorrecta", "Nueva12345")).isInstanceOf(BusinessRuleException.class);
        assertThat(ana.getVersionToken()).isZero();

        service.cambiarPassword("u-1", "Vieja12345", "Nueva12345");
        assertThat(encoder.matches("Nueva12345", ana.getPasswordHash())).isTrue();
        assertThat(ana.getVersionToken()).isEqualTo(1);
    }
}
