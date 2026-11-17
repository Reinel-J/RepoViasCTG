package com.viactg.config;

import com.viactg.exception.InvalidRequestException;
import com.viactg.service.UsuarioService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminInicialTests {
    private final UsuarioService usuarioService = mock(UsuarioService.class);

    @Test
    void unaContrasenaInvalidaSoloSeAvisaYNoImpideArrancar() {
        when(usuarioService.asegurarAdministrador(anyString(), anyString(), anyString()))
                .thenThrow(new InvalidRequestException("La contraseña es demasiado larga"));

        assertThatCode(() -> new AdminInicial(usuarioService, "admin@test.com", "ñ".repeat(40)).run(null)).doesNotThrowAnyException();
    }

    @Test
    void sinCorreoOContrasenaNoHaceNada() {
        new AdminInicial(usuarioService, "", "ClaveAdmin123").run(null);
        new AdminInicial(usuarioService, "admin@test.com", "").run(null);

        verifyNoInteractions(usuarioService);
    }
}
