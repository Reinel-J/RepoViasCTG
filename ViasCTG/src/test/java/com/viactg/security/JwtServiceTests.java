package com.viactg.security;

import com.viactg.model.Rol;
import com.viactg.model.Usuario;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTests {
    private final JwtService jwtService = new JwtService(Base64.getEncoder().encodeToString(new byte[32]), 60_000);

    @Test
    void unTokenDejaDeSerValidoCuandoSubeLaVersionDelUsuario() {
        Usuario usuario = Usuario.builder().id("u-1").email("ana@test.com").rol(Rol.CIUDADANO).activo(true).versionToken(0).build();
        String token = jwtService.generarToken(new UsuarioPrincipal(usuario));
        assertThat(jwtService.esValido(token, new UsuarioPrincipal(usuario))).isTrue();

        usuario.setVersionToken(1);
        assertThat(jwtService.esValido(token, new UsuarioPrincipal(usuario))).isFalse();
        assertThat(jwtService.esValido(jwtService.generarToken(new UsuarioPrincipal(usuario)), new UsuarioPrincipal(usuario))).isTrue();
    }
}
