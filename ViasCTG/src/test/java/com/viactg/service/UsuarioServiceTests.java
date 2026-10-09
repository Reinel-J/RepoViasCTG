package com.viactg.service;

import com.viactg.exception.BusinessRuleException;
import com.viactg.model.Rol;
import com.viactg.model.Usuario;
import com.viactg.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsuarioServiceTests {
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final UsuarioService service = new UsuarioService(usuarioRepository, passwordEncoder);

    @Test
    void unAdministradorNoPuedeCambiarSuPropioRolNiDesactivarse() {
        assertThatThrownBy(() -> service.cambiarRol("admin-1", Rol.CIUDADANO, "admin-1"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.cambiarActivo("admin-1", false, "admin-1"))
                .isInstanceOf(BusinessRuleException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cambiarRolActualizaAOtroUsuario() {
        Usuario usuario = Usuario.builder().id("u-1").rol(Rol.CIUDADANO).activo(true).build();
        when(usuarioRepository.findById("u-1")).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(usuario)).thenReturn(usuario);

        assertThat(service.cambiarRol("u-1", Rol.MODERADOR, "admin-1").getRol()).isEqualTo(Rol.MODERADOR);
    }

    @Test
    void asegurarAdministradorPromueveUnaCuentaExistenteSinCambiarSuContrasena() {
        Usuario existente = Usuario.builder().id("u-1").email("admin@test.com").passwordHash("hash-original")
                .rol(Rol.CIUDADANO).activo(false).build();
        when(usuarioRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(existente));
        when(usuarioRepository.save(existente)).thenReturn(existente);

        Usuario resultado = service.asegurarAdministrador(" Admin@Test.com ", "OtraClave123", "Administrador");

        assertThat(resultado.getRol()).isEqualTo(Rol.ADMIN);
        assertThat(resultado.isActivo()).isTrue();
        assertThat(resultado.getPasswordHash()).isEqualTo("hash-original");
        verify(passwordEncoder, never()).encode(any());
    }
}
